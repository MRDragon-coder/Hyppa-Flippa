package com.hypaflippa.flips.craft;

import com.hypaflippa.market.CoflnetApiClient;
import com.hypaflippa.market.ItemPrices;
import com.hypaflippa.market.MarketStats;
import com.hypaflippa.market.auction.AuctionPrices;
import com.hypaflippa.market.bazaar.BazaarData;
import com.hypaflippa.market.bazaar.BazaarPrices;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.function.Function;

/**
 * Buy the ingredients of a recipe, craft it, sell the result. Ingredients come from wherever
 * they're cheapest: the bazaar, the Auction House (lowest BIN) or an NPC shop. The result is sold
 * on the bazaar (after tax) or, if it isn't a bazaar item, at the lowest BIN (after fees).
 *
 * @param salesPerDay how many of the result sell per day: on the bazaar on the side you sell to,
 *                    on the Auction House by Coflnet's count (null until that's loaded)
 * @param listed      Auction House results only (else null): how many are listed as BIN, a rough
 *                    sign of whether the lowest BIN is a real market price
 * @param buyPerDay   how many of the result you can get ingredients for per day (in items, like
 *                    salesPerDay), limited by the slowest bazaar ingredient; null when no
 *                    ingredient comes from the bazaar (NPC shops and the Auction House aren't counted)
 */
public record CraftFlip(CraftRecipes.Recipe recipe, List<Ingredient> ingredients, double cost,
                        double sellValue, ItemPrices.Source sellSource, Double salesPerDay, Integer listed,
                        Double buyPerDay) {

    public enum Source { BAZAAR, AUCTION, NPC }

    /**
     * {@code count} of one ingredient at {@code unitPrice} each.
     *
     * @param perDay bazaar ingredients only (else null): how many trade per day on the side you
     *               buy from (insta-sells fill buy orders; insta-buys empty sell offers)
     */
    public record Ingredient(String id, int count, double unitPrice, Source source, Double perDay) {
        public double total() {
            return count * unitPrice;
        }
    }

    public String id() {
        return recipe.id();
    }

    /** Coins left over per craft. */
    public double profit() {
        return sellValue - cost;
    }

    public double marginPercent() {
        return profit() / cost * 100;
    }

    /**
     * Coins changing hands per hour in the result's bazaar market, on the side you sell to (like
     * the Bazaar tab's Coins/h). Null for Auction House results.
     */
    public Double coinsPerHour() {
        if (sellSource != ItemPrices.Source.BAZAAR || salesPerDay == null) return null;
        return salesPerDay / 24 * sellValue / recipe.count();
    }

    /**
     * Minutes until a sell offer for the result typically gets undercut. Null when there's no
     * offer to undercut (insta-selling, Auction House results) or while it isn't known yet.
     */
    public Double outbidMinutes(MarketStats stats, BazaarPrices.Mode sellMode) {
        if (sellSource != ItemPrices.Source.BAZAAR || sellMode != BazaarPrices.Mode.ORDER) return null;
        CoflnetApiClient.Outbid outbid = stats.outbid(id());
        return outbid == null ? null : outbid.sellOfferMinutes();
    }

    /**
     * Every craft that clears the filters, at most one per item (its most profitable recipe).
     * Recipes with an ingredient or result that has no price anywhere are left out.
     *
     * @param auctions may be null while the first Auction House scan is still running
     * @param nameOf   the display name of an item, used when sorting by name
     * @param stats    history-based stats of the result (volatility, outbid time) for sorting
     */
    public static List<CraftFlip> find(CraftRecipes recipes, BazaarData bazaar, AuctionPrices auctions,
                                       CraftConfig config, Function<String, String> nameOf, MarketStats stats) {
        Map<String, CraftFlip> best = new HashMap<>();
        recipes:
        for (CraftRecipes.Recipe recipe : recipes.recipes()) {
            if (config.sacksOnly && !recipes.sackable().containsAll(recipe.inputs().keySet())) continue;
            List<Ingredient> ingredients = new ArrayList<>();
            double cost = 0;
            for (Map.Entry<String, Integer> input : recipe.inputs().entrySet()) {
                Ingredient ingredient = cheapest(input.getKey(), input.getValue(), bazaar, auctions,
                        config.allowNpcShop ? recipes.npcShop() : Map.of(), config.buyMode);
                if (ingredient == null) continue recipes;
                ingredients.add(ingredient);
                cost += ingredient.total();
            }
            if (cost <= 0) continue;

            ItemPrices.Price unit = ItemPrices.sellValue(bazaar, auctions, recipe.id(), config.sellMode).orElse(null);
            if (unit == null) continue;
            if (unit.source() == ItemPrices.Source.AUCTION && !config.includeAuction) continue;

            Double salesPerDay = null;
            Integer listed = null;
            BazaarData.Product product = bazaar.getProducts().get(recipe.id());
            if (unit.source() == ItemPrices.Source.BAZAAR && product != null && product.getQuickStatus() != null) {
                BazaarData.QuickStatus status = product.getQuickStatus();
                // A sell offer fills when someone insta-buys; an insta-sell goes into a buy order.
                int perWeek = config.sellMode == BazaarPrices.Mode.ORDER ? status.getBuyMovingWeek() : status.getSellMovingWeek();
                salesPerDay = perWeek / 7.0;
            } else if (unit.source() == ItemPrices.Source.AUCTION) {
                salesPerDay = stats.auctionSalesPerDay(recipe.id());
                listed = auctions.binCount(recipe.id());
            }

            // Crafts a day the slowest bazaar ingredient allows, times the items each craft makes.
            Double buyPerDay = null;
            for (Ingredient ingredient : ingredients) {
                if (ingredient.perDay() == null) continue;
                double items = ingredient.perDay() / ingredient.count() * recipe.count();
                if (buyPerDay == null || items < buyPerDay) buyPerDay = items;
            }

            CraftFlip flip = new CraftFlip(recipe, List.copyOf(ingredients), cost, unit.value() * recipe.count(), unit.source(),
                    salesPerDay, listed, buyPerDay);
            if (flip.profit() < config.minProfit || flip.marginPercent() < config.minMarginPercent) continue;
            if (salesPerDay != null && salesPerDay < config.minSalesPerDay) continue;
            if (listed != null && listed < config.minListed) continue;
            best.merge(recipe.id(), flip, (a, b) -> a.profit() >= b.profit() ? a : b);
        }

        Comparator<CraftFlip> order = switch (config.sortBy) {
            case "name" -> Comparator.comparing((CraftFlip flip) -> nameOf.apply(flip.id()), String.CASE_INSENSITIVE_ORDER);
            case "cost" -> Comparator.comparingDouble(CraftFlip::cost);
            case "sell" -> Comparator.comparingDouble(CraftFlip::sellValue);
            case "margin" -> Comparator.comparingDouble(CraftFlip::marginPercent);
            case "sales" -> Comparator.comparing(CraftFlip::salesPerDay, Comparator.nullsFirst(Comparator.naturalOrder()));
            case "buyable" -> Comparator.comparing(CraftFlip::buyPerDay, Comparator.nullsFirst(Comparator.naturalOrder()));
            case "listed" -> Comparator.comparing(CraftFlip::listed, Comparator.nullsFirst(Comparator.naturalOrder()));
            case "requires" -> Comparator.comparing((CraftFlip flip) -> flip.recipe().requires() == null ? "" : flip.recipe().requires(),
                    String.CASE_INSENSITIVE_ORDER);
            default -> Comparator.comparingDouble(CraftFlip::profit);
        };
        if (!config.sortAscending) order = order.reversed();
        // Columns that are blank for some rows (Auction House results, stats not loaded yet) keep
        // those rows last in either direction.
        switch (config.sortBy) {
            case "coins" -> order = blanksLast(CraftFlip::coinsPerHour, config.sortAscending);
            case "vol" -> order = blanksLast(flip -> stats.volatilityPercent(flip.id()), config.sortAscending);
            case "outbid" -> order = blanksLast(flip -> flip.outbidMinutes(stats, config.sellMode), config.sortAscending);
            default -> {}
        }
        List<CraftFlip> flips = new ArrayList<>(best.values());
        flips.sort(order);
        return flips.size() > config.maxResults ? new ArrayList<>(flips.subList(0, config.maxResults)) : flips;
    }

    private static Comparator<CraftFlip> blanksLast(Function<CraftFlip, Double> value, boolean ascending) {
        Comparator<Double> byValue = ascending ? Comparator.naturalOrder() : Comparator.reverseOrder();
        return Comparator.comparing(value, Comparator.nullsLast(byValue));
    }

    /**
     * The cheapest place to get {@code count} of an item, or null if nobody sells it.
     *
     * @param npcShop NPC shop prices to consider (empty when NPC buying is off)
     */
    private static Ingredient cheapest(String id, int count, BazaarData bazaar, AuctionPrices auctions,
                                       Map<String, Double> npcShop, BazaarPrices.Mode mode) {
        Ingredient best = null;
        OptionalDouble bazaarPrice = BazaarPrices.buyPrice(bazaar, id, mode);
        if (bazaarPrice.isPresent() && bazaarPrice.getAsDouble() > 0) {
            BazaarData.QuickStatus status = bazaar.getProducts().get(id).getQuickStatus();
            Double perDay = status == null ? null
                    : (mode == BazaarPrices.Mode.ORDER ? status.getSellMovingWeek() : status.getBuyMovingWeek()) / 7.0;
            best = new Ingredient(id, count, bazaarPrice.getAsDouble(), Source.BAZAAR, perDay);
        }
        OptionalDouble auctionPrice = auctions == null ? OptionalDouble.empty() : auctions.lowestBin(id);
        if (auctionPrice.isPresent() && (best == null || auctionPrice.getAsDouble() < best.unitPrice())) {
            best = new Ingredient(id, count, auctionPrice.getAsDouble(), Source.AUCTION, null);
        }
        Double npcPrice = npcShop.get(id);
        if (npcPrice != null && npcPrice > 0 && (best == null || npcPrice < best.unitPrice())) {
            best = new Ingredient(id, count, npcPrice, Source.NPC, null);
        }
        return best;
    }
}
