package com.hypaflippa.client;

import com.hypaflippa.HypaFlippa;
import com.hypaflippa.market.CoflnetApiClient;
import com.hypaflippa.market.MarketStats;
import net.minecraft.client.Minecraft;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * History-based stats per bazaar item (volatility, outbid time), fetched from Coflnet on demand:
 * only for the items the Bazaar tab actually shows, one request at a time and well under
 * Coflnet's ~100 requests a minute. Both stats share that one request budget.
 *
 * Everything here runs on the client thread except the downloads, which run one at a time on a
 * daemon thread that hands each result back with {@code Minecraft.execute}.
 */
public final class MarketStatsCache {
    // 750 ms apart = at most 80 a minute, leaving room under Coflnet's limit.
    private static final long REQUEST_SPACING_MS = 750;
    private static final long RATE_LIMIT_PAUSE_MS = 60_000;
    private static final long FAILED_RETRY_MS = 5 * 60_000;

    /** What can be looked up, and how long a value stays good. */
    public enum Kind {
        /** A day's volatility barely changes in half an hour. */
        VOLATILITY(30 * 60_000),
        /** Competition for an item changes faster, and the source window is only an hour. */
        OUTBID(10 * 60_000),
        /** A daily average of Auction House sales moves slowly. */
        AUCTION_SALES(30 * 60_000);

        final long keepMs;

        Kind(long keepMs) {
            this.keepMs = keepMs;
        }
    }

    public enum Status { LOADING, KNOWN, NO_DATA }

    private record Key(Kind kind, String productId) {}

    /** @param value a Double (volatility, Auction House sales) or CoflnetApiClient.Outbid; null unless KNOWN */
    private record Entry(Status status, Object value, long timeMs) {}

    private static final Map<Key, Entry> entries = new HashMap<>();
    private static final Deque<Key> queue = new ArrayDeque<>();
    private static boolean workerRunning;
    private static long pausedUntilMs;
    private static boolean loggedFailure;

    /** The cache as seen by the flip finders. */
    public static final MarketStats STATS = new MarketStats() {
        @Override public Double volatilityPercent(String productId) { return (Double) value(Kind.VOLATILITY, productId); }
        @Override public CoflnetApiClient.Outbid outbid(String productId) { return (CoflnetApiClient.Outbid) value(Kind.OUTBID, productId); }
        @Override public Double auctionSalesPerDay(String itemId) { return (Double) value(Kind.AUCTION_SALES, itemId); }
    };

    private MarketStatsCache() {}

    private static Object value(Kind kind, String productId) {
        Entry entry = entries.get(new Key(kind, productId));
        return entry != null && entry.status() == Status.KNOWN ? entry.value() : null;
    }

    public static Status status(Kind kind, String productId) {
        Entry entry = entries.get(new Key(kind, productId));
        return entry == null ? Status.LOADING : entry.status();
    }

    /** Makes sure these stats of these items get (re)fetched when they have no fresh value. Cheap to call often. */
    public static void request(Collection<String> productIds, Set<Kind> kinds) {
        if (kinds.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (String id : productIds) {
            for (Kind kind : kinds) {
                Key key = new Key(kind, id);
                Entry entry = entries.get(key);
                boolean fresh = entry != null && (entry.status() == Status.LOADING
                        || now - entry.timeMs() < (entry.status() == Status.KNOWN ? kind.keepMs : FAILED_RETRY_MS));
                if (fresh) continue;
                entries.put(key, new Entry(Status.LOADING, null, now));
                queue.add(key);
            }
        }
        startWorker();
    }

    private static Optional<Object> fetch(Key key) throws Exception {
        return switch (key.kind()) {
            case VOLATILITY -> {
                var percent = CoflnetApiClient.fetchVolatilityPercent(key.productId());
                yield percent.isPresent() ? Optional.of(percent.getAsDouble()) : Optional.empty();
            }
            case OUTBID -> CoflnetApiClient.fetchOutbid(key.productId()).map(outbid -> (Object) outbid);
            case AUCTION_SALES -> {
                var perDay = CoflnetApiClient.fetchAuctionSalesPerDay(key.productId());
                yield perDay.isPresent() ? Optional.of(perDay.getAsDouble()) : Optional.empty();
            }
        };
    }

    private static void startWorker() {
        if (workerRunning || queue.isEmpty()) return;
        workerRunning = true;
        Key key = queue.poll();
        long delay = Math.max(REQUEST_SPACING_MS, pausedUntilMs - System.currentTimeMillis());

        Thread thread = new Thread(() -> {
            Optional<Object> result = Optional.empty();
            boolean rateLimited = false;
            try {
                Thread.sleep(delay);
                result = fetch(key);
            } catch (CoflnetApiClient.RateLimitedException e) {
                rateLimited = true;
            } catch (Exception e) {
                if (!loggedFailure) {
                    loggedFailure = true; // once per session: an outage would otherwise flood the log
                    HypaFlippa.LOGGER.warn("Couldn't fetch price history from Coflnet ({}); market stats will show '-'", e.toString());
                }
            }
            Optional<Object> finalResult = result;
            boolean finalRateLimited = rateLimited;
            Minecraft.getInstance().execute(() -> finish(key, finalResult, finalRateLimited));
        }, "HypaFlippa-MarketStats");
        thread.setDaemon(true);
        thread.start();
    }

    private static void finish(Key key, Optional<Object> result, boolean rateLimited) {
        workerRunning = false;
        long now = System.currentTimeMillis();
        if (rateLimited) {
            // Try this one again after a pause instead of hammering the API.
            pausedUntilMs = now + RATE_LIMIT_PAUSE_MS;
            queue.addFirst(key);
        } else {
            entries.put(key, result.isPresent() ? new Entry(Status.KNOWN, result.get(), now) : new Entry(Status.NO_DATA, null, now));
            // Filters, sorting and the "..." in cells may change with this.
            Features.statsArrived();
        }
        startWorker();
    }
}
