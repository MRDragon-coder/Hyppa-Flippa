package com.hypaflippa.flips.bazaar;

import com.hypaflippa.market.CoflnetApiClient;
import com.hypaflippa.market.MarketStats;
import com.hypaflippa.market.bazaar.BazaarData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Buy on the bazaar, sell to an NPC. Two ways to buy: a buy order at the top buy-order price
 * (cheaper, but you wait for it to fill) or insta-buy from the cheapest sell offer (immediate).
 * NPCs pay a fixed price and take no tax.
 *
 * @param instaBuyPrice NaN when nobody is selling right now (no sell offers)
 */
public record NpcFlip(String productId, double buyOrderPrice, double instaBuyPrice, double npcPrice,
                      int sellMovingWeek, int buyMovingWeek) {

    public boolean hasInstaBuy() {
        return !Double.isNaN(instaBuyPrice);
    }

    public double orderMarginPercent() {
        return (npcPrice - buyOrderPrice) / buyOrderPrice * 100;
    }

    /**
     * Coins per hour on the buying side: players insta-selling into buy orders (sellMovingWeek,
     * at about the buy-order price), over Hypixel's 7-day volume window. NPCs buy any amount, so
     * the selling side is never the bottleneck.
     */
    public double coinsPerHour() {
        return sellMovingWeek * buyOrderPrice / 168;
    }

    /** NaN without sell offers. */
    public double instaMarginPercent() {
        return (npcPrice - instaBuyPrice) / instaBuyPrice * 100;
    }

    /**
     * Every bazaar item an NPC buys for more than the top buy order. {@code minProfitPercent}
     * filters on the buy-order margin (always the better of the two, since a buy order is never
     * above the cheapest sell offer); {@code minInstaProfitPercent}, when set, on the insta margin.
     *
     * @param npcPrices NPC sell price per item id (from Hypixel's item list)
     * @param nameOf the display name of a product id, used when sorting by name
     * @param stats  history-based stats (volatility, outbid time) for filtering and sorting
     */
    public static List<NpcFlip> find(BazaarData data, Map<String, Double> npcPrices, FlipFilters filters,
                                     Function<String, String> nameOf, MarketStats stats) {
        List<NpcFlip> flips = new ArrayList<>();
        for (Map.Entry<String, BazaarData.Product> entry : data.getProducts().entrySet()) {
            Double npcPrice = npcPrices.get(entry.getKey());
            BazaarData.Product product = entry.getValue();
            BazaarData.QuickStatus quickStatus = product.getQuickStatus();
            List<BazaarData.SummaryEntry> buyOrders = product.getBuyOrders();
            List<BazaarData.SummaryEntry> sellOffers = product.getSellOffers();
            if (npcPrice == null || npcPrice <= 0 || quickStatus == null || buyOrders == null || buyOrders.isEmpty()) {
                continue;
            }
            if (quickStatus.getSellMovingWeek() < filters.minSellMovingWeek || quickStatus.getBuyMovingWeek() < filters.minBuyMovingWeek) {
                continue;
            }
            double buyOrderPrice = buyOrders.get(0).getPricePerUnit();
            double instaBuyPrice = sellOffers == null || sellOffers.isEmpty() ? Double.NaN : sellOffers.get(0).getPricePerUnit();
            if (buyOrderPrice <= 0) continue;

            NpcFlip flip = new NpcFlip(entry.getKey(), buyOrderPrice, instaBuyPrice, npcPrice,
                    quickStatus.getSellMovingWeek(), quickStatus.getBuyMovingWeek());
            if (flip.orderMarginPercent() < filters.minProfitPercent) continue;
            // With the insta filter on, items you can't insta-buy (no sell offers) are left out too.
            if (filters.minInstaProfitPercent != null
                    && (!flip.hasInstaBuy() || flip.instaMarginPercent() < filters.minInstaProfitPercent)) continue;
            if (filters.minCoinsPerHour != null && flip.coinsPerHour() < filters.minCoinsPerHour) continue;
            if (filters.hiddenByStats(stats.volatilityPercent(entry.getKey()), outbidMinutes(stats, entry.getKey()))) continue;
            flips.add(flip);
        }

        // Ascending comparators, flipped for descending. Rows without insta-buy sort last on the
        // insta columns either way.
        Comparator<NpcFlip> order = switch (filters.sortBy) {
            case "name" -> Comparator.comparing((NpcFlip flip) -> nameOf.apply(flip.productId()), String.CASE_INSENSITIVE_ORDER);
            case "order" -> Comparator.comparingDouble(NpcFlip::buyOrderPrice);
            case "insta" -> Comparator.comparingDouble(NpcFlip::instaBuyPrice);
            case "npc" -> Comparator.comparingDouble(NpcFlip::npcPrice);
            case "instaMargin" -> Comparator.comparingDouble(NpcFlip::instaMarginPercent);
            case "coins" -> Comparator.comparingDouble(NpcFlip::coinsPerHour);
            default -> Comparator.comparingDouble(NpcFlip::orderMarginPercent);
        };
        if (!filters.sortAscending) order = order.reversed();
        if (filters.sortBy.startsWith("insta")) {
            order = Comparator.comparing((NpcFlip flip) -> !flip.hasInstaBuy()).thenComparing(order);
        }
        if (filters.sortBy.equals("vol")) order = filters.statOrder((NpcFlip flip) -> stats.volatilityPercent(flip.productId()));
        if (filters.sortBy.equals("outbid")) order = filters.statOrder((NpcFlip flip) -> outbidMinutes(stats, flip.productId()));
        flips.sort(order);
        return flips.size() > filters.maxResults ? new ArrayList<>(flips.subList(0, filters.maxResults)) : flips;
    }

    /**
     * Minutes until an NPC flip needs attention: only a buy order can get outbid (NPCs don't
     * compete, and insta-buying has no order to beat). Null while unknown.
     */
    public static Double outbidMinutes(MarketStats stats, String productId) {
        CoflnetApiClient.Outbid outbid = stats.outbid(productId);
        return outbid == null ? null : outbid.buyOrderMinutes();
    }
}
