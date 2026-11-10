package com.hypaflippa.flips.bazaar;

import com.hypaflippa.market.CoflnetApiClient;
import com.hypaflippa.market.MarketStats;
import com.hypaflippa.market.bazaar.BazaarData;
import com.hypaflippa.market.bazaar.BazaarPrices;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** One order flip: buy with a buy order at buyPrice, sell with a sell offer at sellPrice. */
public class BazaarFlip {
    private final String productId;
    private final double buyPrice;
    private final double sellPrice;
    private final int maxQuantity;
    private final double profitPerUnit;
    private final double profitPercent;
    private final int sellMovingWeek;
    private final int buyMovingWeek;

    public BazaarFlip(String productId, double buyPrice, double sellPrice, int maxQuantity, int sellMovingWeek, int buyMovingWeek) {
        this.productId = productId;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.maxQuantity = maxQuantity;
        this.profitPerUnit = BazaarPrices.afterTax(sellPrice) - buyPrice;
        this.profitPercent = buyPrice > 0 ? (profitPerUnit / buyPrice) * 100 : 0;
        this.sellMovingWeek = sellMovingWeek;
        this.buyMovingWeek = buyMovingWeek;
    }

    public String getProductId() { return productId; }
    public double getBuyPrice() { return buyPrice; }
    public double getSellPrice() { return sellPrice; }
    public int getMaxQuantity() { return maxQuantity; }
    public double getProfitPerUnit() { return profitPerUnit; }
    public double getProfitPercent() { return profitPercent; }
    public int getSellMovingWeek() { return sellMovingWeek; }
    public int getBuyMovingWeek() { return buyMovingWeek; }

    /**
     * Coins per hour flowing through the slower side of this flip. Your buy order fills from
     * players insta-selling (sellMovingWeek, at about the buy price) and your sell offer from
     * players insta-buying (buyMovingWeek, at about the sell price); a flip goes no faster than
     * the slower of the two. Hypixel's volumes cover the last 7 days (168 hours).
     */
    public double coinsPerHour() {
        double buySide = sellMovingWeek * buyPrice;
        double sellSide = buyMovingWeek * sellPrice;
        return Math.min(buySide, sellSide) / 168;
    }

    /**
     * Flags order books that look rigged: a top buy order far above the usual buy-order price,
     * a sell offer far below the usual sell-offer price, or a single price level holding more
     * than half a week's trading volume. quick_status prices are averages over the order book,
     * so they are the "usual" baseline.
     */
    public static boolean isManipulated(BazaarData.Product product, double maxSpreadRatio) {
        BazaarData.QuickStatus quickStatus = product.getQuickStatus();
        BazaarData.SummaryEntry topBuyOrder = product.getBuyOrders().get(0);
        BazaarData.SummaryEntry lowestSellOffer = product.getSellOffers().get(0);

        // quick_status.sellPrice is the buy-order side, quick_status.buyPrice the sell-offer side.
        double buyOrderSpread = quickStatus.getSellPrice() > 0 ? topBuyOrder.getPricePerUnit() / quickStatus.getSellPrice() : 1;
        double sellOfferSpread = quickStatus.getBuyPrice() > 0 ? quickStatus.getBuyPrice() / lowestSellOffer.getPricePerUnit() : 1;

        double buyOrderConcentration = quickStatus.getSellMovingWeek() > 0 ? (double) topBuyOrder.getAmount() / quickStatus.getSellMovingWeek() : 0;
        double sellOfferConcentration = quickStatus.getBuyMovingWeek() > 0 ? (double) lowestSellOffer.getAmount() / quickStatus.getBuyMovingWeek() : 0;

        return buyOrderSpread > maxSpreadRatio
                || sellOfferSpread > maxSpreadRatio
                || buyOrderConcentration > 0.5
                || sellOfferConcentration > 0.5;
    }

    /**
     * @param nameOf the display name of a product id, used when sorting by name
     * @param stats  history-based stats (volatility, outbid time) for filtering and sorting
     */
    public static List<BazaarFlip> find(BazaarData data, FlipFilters filters, double maxSpreadRatio,
                                      Function<String, String> nameOf, MarketStats stats) {
        List<BazaarFlip> flips = new ArrayList<>();

        for (Map.Entry<String, BazaarData.Product> entry : data.getProducts().entrySet()) {
            String productId = entry.getKey();
            BazaarData.Product product = entry.getValue();

            List<BazaarData.SummaryEntry> buyOrders = product.getBuyOrders();
            List<BazaarData.SummaryEntry> sellOffers = product.getSellOffers();
            BazaarData.QuickStatus quickStatus = product.getQuickStatus();

            if (buyOrders == null || buyOrders.isEmpty() || sellOffers == null || sellOffers.isEmpty() || quickStatus == null) {
                continue;
            }

            if (quickStatus.getSellMovingWeek() < filters.minSellMovingWeek || quickStatus.getBuyMovingWeek() < filters.minBuyMovingWeek) {
                continue;
            }

            if (isManipulated(product, maxSpreadRatio)) {
                continue;
            }

            BazaarData.SummaryEntry topBuyOrder = buyOrders.get(0);
            BazaarData.SummaryEntry lowestSellOffer = sellOffers.get(0);

            // An order flip: place a buy order at the top buy-order price, then a sell offer at
            // the lowest sell-offer price. The margin is the gap between the two (minus tax).
            double buyPrice = topBuyOrder.getPricePerUnit();
            double sellPrice = lowestSellOffer.getPricePerUnit();
            int maxQuantity = Math.min(topBuyOrder.getAmount(), lowestSellOffer.getAmount());

            if (maxQuantity <= 0 || sellPrice <= buyPrice) {
                continue;
            }

            BazaarFlip flip = new BazaarFlip(productId, buyPrice, sellPrice, maxQuantity, quickStatus.getSellMovingWeek(), quickStatus.getBuyMovingWeek());
            if (flip.getProfitPercent() >= filters.minProfitPercent
                    && (filters.minCoinsPerHour == null || flip.coinsPerHour() >= filters.minCoinsPerHour)
                    && !filters.hiddenByStats(stats.volatilityPercent(productId), outbidMinutes(stats, productId))) {
                flips.add(flip);
            }
        }

        // Comparators are ascending; flipped for descending. Sorting happens before the cut
        // to maxResults, so the cut keeps the top entries in the chosen order.
        Comparator<BazaarFlip> order = switch (filters.sortBy) {
            case "name" -> Comparator.comparing((BazaarFlip flip) -> nameOf.apply(flip.getProductId()), String.CASE_INSENSITIVE_ORDER);
            case "sell" -> Comparator.comparingDouble(BazaarFlip::getSellPrice);
            case "margin" -> Comparator.comparingDouble(BazaarFlip::getProfitPercent);
            case "coins" -> Comparator.comparingDouble(BazaarFlip::coinsPerHour);
            default -> Comparator.comparingDouble(BazaarFlip::getBuyPrice);
        };
        if (filters.sortBy.equals("vol")) {
            flips.sort(filters.statOrder((BazaarFlip flip) -> stats.volatilityPercent(flip.getProductId())));
        } else if (filters.sortBy.equals("outbid")) {
            flips.sort(filters.statOrder((BazaarFlip flip) -> outbidMinutes(stats, flip.getProductId())));
        } else {
            flips.sort(filters.sortAscending ? order : order.reversed());
        }
        return flips.size() > filters.maxResults ? new ArrayList<>(flips.subList(0, filters.maxResults)) : flips;
    }

    /**
     * Minutes until an order flip needs attention: it has a buy order and a sell offer, and
     * whichever gets beaten first is when you have to act. Null while unknown.
     */
    public static Double outbidMinutes(MarketStats stats, String productId) {
        CoflnetApiClient.Outbid outbid = stats.outbid(productId);
        return outbid == null ? null : outbid.fastest();
    }
}
