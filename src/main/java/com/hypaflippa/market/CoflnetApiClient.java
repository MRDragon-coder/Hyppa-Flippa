package com.hypaflippa.market;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Market statistics from Coflnet's public price history (sky.coflnet.com). Hypixel's own API only
 * has the current order book and listings, so anything about what happened over time comes from here.
 *
 * Coflnet allows about 100 requests a minute and one request covers one item, so callers must
 * only ask for items they actually show, and cache the results.
 */
public final class CoflnetApiClient {
    private static final String HISTORY_URL = "https://sky.coflnet.com/api/bazaar/%s/history/%s";
    private static final String AUCTION_PRICE_URL = "https://sky.coflnet.com/api/item/price/%s%s";
    private static final String USER_AGENT = "HypaFlippa (+https://github.com/MRDragon-coder/Hyppa-Flippa)";
    private static final Duration VOLATILITY_WINDOW = Duration.ofHours(24);
    /** Fewer usable points than this (about an hour of the daily history) isn't enough to say anything. */
    private static final int MIN_DAY_POINTS = 12;
    /** About 10 minutes of the hourly history (one point every 20 seconds). */
    private static final int MIN_HOUR_POINTS = 30;
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** One point of Coflnet's history. buy = insta-buy price, sell = insta-sell price; null when that side was empty. */
    private static final class Point {
        Double buy;
        Double sell;
        String timestamp;
        transient LocalDateTime time;
    }

    /**
     * Average minutes between new better orders over the last hour, per side;
     * {@code Double.POSITIVE_INFINITY} when it didn't happen at all.
     *
     * @param buyOrderMinutes  how long a top buy order typically lasts before someone bids higher
     * @param sellOfferMinutes how long a cheapest sell offer typically lasts before someone undercuts it
     */
    public record Outbid(double buyOrderMinutes, double sellOfferMinutes) {
        /** An order flip has a buy order and a sell offer; whichever gets beaten first is when you have to act. */
        public double fastest() {
            return Math.min(buyOrderMinutes, sellOfferMinutes);
        }
    }

    /** The part of Coflnet's Auction House price summary that gets used. */
    private static final class AuctionPrice {
        Double volume;
    }

    /** Thrown when Coflnet says we're asking too often; the caller should pause for a while. */
    public static final class RateLimitedException extends Exception {
        public RateLimitedException() {
            super("Coflnet rate limit reached");
        }
    }

    private CoflnetApiClient() {}

    /**
     * The typical price swing over the last 24 hours as a percentage: the standard deviation of
     * the middle price (halfway between insta-buy and insta-sell), divided by its average.
     * Roughly: "the price usually stays within ± this much". Empty when there's too little data.
     */
    public static OptionalDouble fetchVolatilityPercent(String productId) throws Exception {
        List<Point> points = fetchHistory(productId, "day");
        if (points.isEmpty()) return OptionalDouble.empty();
        LocalDateTime newest = points.getLast().time;

        List<Double> mids = new ArrayList<>();
        for (Point point : points) {
            if (Duration.between(point.time, newest).compareTo(VOLATILITY_WINDOW) > 0) continue;
            if (!hasBuy(point) || !hasSell(point)) continue;
            mids.add((point.buy + point.sell) / 2);
        }
        if (mids.size() < MIN_DAY_POINTS) return OptionalDouble.empty();

        double mean = mids.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        if (mean <= 0) return OptionalDouble.empty();
        double variance = mids.stream().mapToDouble(mid -> (mid - mean) * (mid - mean)).average().orElse(0);
        return OptionalDouble.of(Math.sqrt(variance) / mean * 100);
    }

    /**
     * How often the top orders got beaten during the last hour. The history has a point every
     * 20 seconds: when the top buy-order price goes up between two points, someone placed a
     * higher buy order (an outbid); when it goes down, the top order was filled or cancelled,
     * which doesn't count. Sell offers the other way round (cheapest price going down = undercut).
     * Several outbids within one 20-second step count as one.
     */
    public static Optional<Outbid> fetchOutbid(String productId) throws Exception {
        List<Point> points = fetchHistory(productId, "hour");
        if (points.size() < MIN_HOUR_POINTS) return Optional.empty();
        double minutes = Duration.between(points.getFirst().time, points.getLast().time).toSeconds() / 60.0;
        if (minutes <= 0) return Optional.empty();

        int outbids = 0, undercuts = 0;
        for (int i = 1; i < points.size(); i++) {
            Point before = points.get(i - 1), after = points.get(i);
            if (hasSell(before) && hasSell(after) && after.sell > before.sell + 1e-6) outbids++;
            if (hasBuy(before) && hasBuy(after) && after.buy < before.buy - 1e-6) undercuts++;
        }
        return Optional.of(new Outbid(perEvent(minutes, outbids), perEvent(minutes, undercuts)));
    }

    private static double perEvent(double minutes, int events) {
        return events == 0 ? Double.POSITIVE_INFINITY : minutes / events;
    }

    /**
     * How many of an Auction House item sold per day recently, by Coflnet's count of ended
     * auctions. Empty when Coflnet doesn't know the item.
     *
     * @param itemId a price key: an item id, or {@code PET:NAME:RARITY}
     */
    public static OptionalDouble fetchAuctionSalesPerDay(String itemId) throws Exception {
        String tag = itemId, query = "";
        if (itemId.startsWith("PET:")) {
            String[] parts = itemId.split(":");
            tag = "PET_" + parts[1];
            query = "?Rarity=" + parts[2];
        }
        String body = get(String.format(AUCTION_PRICE_URL, URLEncoder.encode(tag, StandardCharsets.UTF_8), query));
        AuctionPrice price = body == null ? null : GSON.fromJson(body, AuctionPrice.class);
        return price == null || price.volume == null ? OptionalDouble.empty() : OptionalDouble.of(price.volume);
    }

    /** The history of one item, oldest point first (Coflnet sends newest first). Empty when it has none. */
    private static List<Point> fetchHistory(String productId, String range) throws Exception {
        String tag = URLEncoder.encode(productId, StandardCharsets.UTF_8); // e.g. RAW_FISH:1
        String body = get(String.format(HISTORY_URL, tag, range));
        if (body == null) return List.of(); // item has no history

        List<Point> points = GSON.fromJson(body, new TypeToken<List<Point>>() {}.getType());
        if (points == null) return List.of();
        List<Point> usable = new ArrayList<>();
        for (Point point : points) {
            // Timestamps are UTC without a zone; only their differences matter here.
            point.time = parse(point.timestamp);
            if (point.time != null) usable.add(point);
        }
        usable.sort(Comparator.comparing((Point point) -> point.time));
        return usable;
    }

    /** The response body, or null when Coflnet doesn't know the item. */
    private static String get(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 429) throw new RateLimitedException();
        if (response.statusCode() == 400 || response.statusCode() == 404) return null;
        if (response.statusCode() != 200) throw new IllegalStateException("HTTP " + response.statusCode());
        return response.body();
    }

    private static boolean hasBuy(Point point) {
        return point.buy != null && point.buy > 0;
    }

    private static boolean hasSell(Point point) {
        return point.sell != null && point.sell > 0;
    }

    private static LocalDateTime parse(String timestamp) {
        try {
            return timestamp == null ? null : LocalDateTime.parse(timestamp);
        } catch (Exception e) {
            return null;
        }
    }
}
