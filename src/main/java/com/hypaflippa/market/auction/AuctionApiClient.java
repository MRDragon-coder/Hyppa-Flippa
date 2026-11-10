package com.hypaflippa.market.auction;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypaflippa.HypaFlippa;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Reads every page of Hypixel's public Auction House API (no key needed) and works out the lowest
 * Buy-It-Now price per item. A full scan is ~50 pages / ~48k auctions and takes a few seconds,
 * so this must only ever run off the game thread.
 */
public final class AuctionApiClient {
    private static final String AUCTIONS_URL = "https://api.hypixel.net/v2/skyblock/auctions?page=";
    private static final int PARALLEL_PAGES = 8;
    private static final int ATTEMPTS_PER_PAGE = 3;
    private static final Gson GSON = new Gson();
    // HTTP/1.1: in-game, an HTTP/2 response once arrived cut off ~1.7 MB into a page.
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** The outcome of one full scan. {@code skippedPages} failed every attempt and are missing. */
    public record Scan(AuctionPrices prices, int pages, int skippedPages, long millis) {}

    // Only the fields we use; Gson ignores the rest of each auction.
    private static final class Page {
        boolean success;
        int totalPages;
        List<Auction> auctions = new ArrayList<>();
    }

    private static final class Auction {
        boolean bin;
        double starting_bid;
        String item_bytes;
    }

    private AuctionApiClient() {}

    public static Scan fetchAll() throws Exception {
        long start = System.currentTimeMillis();
        // Page 0 tells us how many pages there are, so without it there is no scan.
        Page first = fetchPage(0);

        Map<String, Double> lowest = new HashMap<>();
        Map<String, Integer> counts = new HashMap<>();
        int binAuctions = collect(first, lowest, counts);

        ExecutorService pool = Executors.newFixedThreadPool(PARALLEL_PAGES, runnable -> {
            Thread thread = new Thread(runnable, "HypaFlippa-Auctions");
            thread.setDaemon(true); // never keeps the game from closing
            return thread;
        });
        try {
            List<Future<Page>> pages = new ArrayList<>();
            for (int page = 1; page < first.totalPages; page++) {
                int number = page;
                pages.add(pool.submit(() -> fetchPage(number)));
            }
            int skipped = 0;
            for (Future<Page> page : pages) {
                try {
                    binAuctions += collect(page.get(), lowest, counts);
                } catch (ExecutionException e) {
                    // This page failed every attempt; the other pages still give good prices.
                    skipped++;
                }
            }
            AuctionPrices prices = new AuctionPrices(Map.copyOf(lowest), Map.copyOf(counts), binAuctions, System.currentTimeMillis());
            return new Scan(prices, first.totalPages, skipped, System.currentTimeMillis() - start);
        } finally {
            pool.shutdownNow();
        }
    }

    /** Downloads and parses one page, retrying when the response is cut off or broken. */
    private static Page fetchPage(int page) throws Exception {
        Exception last = null;
        for (int attempt = 1; attempt <= ATTEMPTS_PER_PAGE; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(AUCTIONS_URL + page))
                        .timeout(Duration.ofSeconds(30))
                        .GET()
                        .build();
                HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) throw new IllegalStateException("HTTP " + response.statusCode());
                Page result = GSON.fromJson(response.body(), Page.class);
                if (result == null || !result.success) throw new IllegalStateException("success=false");
                return result;
            } catch (Exception e) {
                last = e;
                Thread.sleep(500L * attempt);
            }
        }
        throw new IllegalStateException("Auction page " + page + " failed " + ATTEMPTS_PER_PAGE + " times", last);
    }

    /** Folds one page's BIN auctions into {@code lowest} and {@code counts}; returns how many BIN auctions it had. */
    private static int collect(Page page, Map<String, Double> lowest, Map<String, Integer> counts) {
        int count = 0;
        for (Auction auction : page.auctions) {
            if (!auction.bin || auction.item_bytes == null) continue;
            try {
                Map<String, Object> root = Nbt.readCompressed(Base64.getDecoder().decode(auction.item_bytes));
                List<Object> items = Nbt.getList(root, "i");
                if (items == null || items.isEmpty() || !(items.getFirst() instanceof Map<?, ?>)) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> item = (Map<String, Object>) items.getFirst();
                String key = priceKey(Nbt.getCompound(Nbt.getCompound(item, "tag"), "ExtraAttributes"));
                if (key == null) continue;
                // starting_bid is for the whole stack.
                double perUnit = auction.starting_bid / Math.max(1, Nbt.getInt(item, "Count", 1));
                lowest.merge(key, perUnit, Math::min);
                counts.merge(key, 1, Integer::sum);
                count++;
            } catch (Exception ignored) {
                // One unreadable item shouldn't spoil the whole scan.
            }
        }
        return count;
    }

    /**
     * The key an item is priced under: its SkyBlock id, except runes ({@code RUNE:JERRY:3}) and
     * pets ({@code PET:SNOWMAN:LEGENDARY}), which share one id across all types and levels.
     */
    static String priceKey(Map<String, Object> extraAttributes) {
        String id = Nbt.getString(extraAttributes, "id");
        if (id == null) return null;
        if (id.equals("RUNE") || id.equals("UNIQUE_RUNE")) {
            Map<String, Object> runes = Nbt.getCompound(extraAttributes, "runes");
            if (runes == null || runes.isEmpty()) return id;
            Map.Entry<String, Object> rune = runes.entrySet().iterator().next();
            return "RUNE:" + rune.getKey() + ":" + rune.getValue();
        }
        if (id.equals("PET")) {
            String petInfo = Nbt.getString(extraAttributes, "petInfo");
            if (petInfo == null) return id;
            JsonObject info = JsonParser.parseString(petInfo).getAsJsonObject();
            if (!info.has("type") || !info.has("tier")) return id;
            return "PET:" + info.get("type").getAsString() + ":" + info.get("tier").getAsString();
        }
        return id;
    }
}
