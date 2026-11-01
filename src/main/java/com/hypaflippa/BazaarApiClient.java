package com.hypaflippa;

import com.hypaflippa.BazaarData;
import com.hypaflippa.BazaarFlip;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class BazaarApiClient {

    private static final String BAZAAR_URL = "https://api.hypixel.net/v2/skyblock/bazaar";
    private static final Gson GSON = new GsonBuilder().create();
    // Shared and time-limited: a hung request would otherwise block every later refresh.
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public static BazaarData fetchBazaarData() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BAZAAR_URL))
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

        return GSON.fromJson(response.body(), BazaarData.class);
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

    /** @param nameOf the display name of a product id, used when sorting by name */
    public static List<BazaarFlip> findFlips(BazaarData data, FlipFilters filters, double maxSpreadRatio, Function<String, String> nameOf) {
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
            if (flip.getProfitPercent() >= filters.minProfitPercent) {
                flips.add(flip);
            }
        }

        // Comparators are ascending; flipped for descending. Sorting happens before the cut
        // to maxResults, so the cut keeps the top entries in the chosen order.
        Comparator<BazaarFlip> order = switch (filters.sortBy) {
            case "name" -> Comparator.comparing((BazaarFlip flip) -> nameOf.apply(flip.getProductId()), String.CASE_INSENSITIVE_ORDER);
            case "sell" -> Comparator.comparingDouble(BazaarFlip::getSellPrice);
            case "margin" -> Comparator.comparingDouble(BazaarFlip::getProfitPercent);
            default -> Comparator.comparingDouble(BazaarFlip::getBuyPrice);
        };
        flips.sort(filters.sortAscending ? order : order.reversed());
        return flips.size() > filters.maxResults ? new ArrayList<>(flips.subList(0, filters.maxResults)) : flips;
    }
}