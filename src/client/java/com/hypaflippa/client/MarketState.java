package com.hypaflippa.client;

import com.hypaflippa.HypaFlippa;
import com.hypaflippa.client.ui.Ui;
import com.hypaflippa.flips.craft.CraftRecipes;
import com.hypaflippa.market.ItemsApiClient;
import com.hypaflippa.market.auction.AuctionApiClient;
import com.hypaflippa.market.auction.AuctionPrices;
import com.hypaflippa.market.bazaar.BazaarApiClient;
import com.hypaflippa.market.bazaar.BazaarData;
import net.minecraft.client.Minecraft;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The latest market data every feature works from: the bazaar, the Auction House and Hypixel's
 * item list (NPC prices and names). Tabs read from here every frame instead of keeping their own
 * copy, so a background refresh shows up on screen automatically; whenever something new comes
 * in, every feature recomputes ({@link Features#recomputeAll}).
 *
 * All fields are only touched on the client thread; the HTTP fetches run off-thread and hop back
 * with {@code Minecraft.execute} before writing anything.
 */
public final class MarketState {
    private static final long AUTO_REFRESH_MS = 30_000;
    // A full Auction House scan is ~50 pages, and prices there move slowly: every 5 minutes.
    private static final long AUCTION_REFRESH_MS = 5 * 60_000;
    // The refresh button re-scans the Auction House only if the last scan is at least this old.
    private static final long AUCTION_MANUAL_REFRESH_MS = 60_000;
    // After a failed scan, try again this soon instead of waiting the full 5 minutes.
    private static final long AUCTION_RETRY_MS = 30_000;

    private static BazaarData data; // null until the first successful fetch
    private static boolean fetching;
    private static long lastFetchMs;
    private static String error;

    private static Map<String, Double> npcPrices; // item id -> NPC sell price; loaded once per session
    private static Map<String, String> itemNames = Map.of(); // item id -> display name, same list
    private static boolean fetchingNpcPrices;
    private static long lastNpcPriceAttemptMs;

    private static AuctionPrices auctions; // null until the first successful scan
    private static boolean fetchingAuctions;
    private static long lastAuctionFetchMs;
    private static boolean auctionFetchFailed;

    private MarketState() {}

    /** The latest bazaar snapshot, null until the first successful fetch. */
    public static BazaarData data() { return data; }
    public static boolean isFetching() { return fetching; }
    public static long lastFetchMs() { return lastFetchMs; }
    public static String error() { return error; }

    /** NPC sell price per item id, null until Hypixel's item list is in. */
    public static Map<String, Double> npcPrices() { return npcPrices; }

    /** The in-game name of an item ("RAW_FISH:1" -> "Raw Salmon"), or a tidied id until the item list is in. */
    public static String itemName(String productId) {
        String name = itemNames.get(productId);
        if (name == null) name = CraftRecipes.get().names().get(productId); // books and pets, which Hypixel's list lacks
        return name != null ? name : Ui.displayName(productId);
    }

    /** Lowest-BIN prices from the Auction House, null until the first scan finishes. */
    public static AuctionPrices auctions() { return auctions; }
    public static boolean isFetchingAuctions() { return fetchingAuctions; }
    public static boolean auctionFetchFailed() { return auctionFetchFailed; }

    /** The refresh button: bazaar now, Auction House too unless it was scanned within the last minute. */
    public static void refreshAll() {
        refresh();
        if (System.currentTimeMillis() - lastAuctionFetchMs >= AUCTION_MANUAL_REFRESH_MS) refreshAuctions();
    }

    /** Fetches fresh bazaar data in the background. Ignored if a fetch is already running. */
    public static void refresh() {
        if (fetching) return;
        fetching = true;

        Minecraft client = Minecraft.getInstance();
        CompletableFuture.supplyAsync(() -> {
            try {
                return BazaarApiClient.fetchBazaarData();
            } catch (Exception e) {
                HypaFlippa.LOGGER.error("Failed to fetch bazaar data", e);
                return null;
            }
        }).thenAccept(result -> client.execute(() -> {
            fetching = false;
            // Also stamped on failure, so a broken connection retries every 30s instead of every tick.
            lastFetchMs = System.currentTimeMillis();
            if (result == null || !result.isSuccess()) {
                error = "Failed to fetch bazaar data";
                return;
            }
            error = null;
            data = result;
            Features.recomputeAll();
        }));
    }

    /** Scans the Auction House in the background. Ignored if a scan is already running. */
    public static void refreshAuctions() {
        if (fetchingAuctions) return;
        fetchingAuctions = true;

        Minecraft client = Minecraft.getInstance();
        CompletableFuture.supplyAsync(() -> {
            try {
                AuctionApiClient.Scan scan = AuctionApiClient.fetchAll();
                HypaFlippa.LOGGER.info("Auction House scan: {} BIN auctions, {} items, {}/{} pages in {} ms",
                        scan.prices().binAuctions(), scan.prices().lowestBin().size(),
                        scan.pages() - scan.skippedPages(), scan.pages(), scan.millis());
                return scan.prices();
            } catch (Exception e) {
                HypaFlippa.LOGGER.error("Failed to fetch Auction House data", e);
                return null;
            }
        }).thenAccept(result -> client.execute(() -> {
            fetchingAuctions = false;
            auctionFetchFailed = result == null;
            // A failed scan is retried after 30s: pretend the last scan was that much "newer" than 5 min ago.
            lastAuctionFetchMs = result != null ? System.currentTimeMillis()
                    : System.currentTimeMillis() - AUCTION_REFRESH_MS + AUCTION_RETRY_MS;
            // On failure keep the previous prices: slightly old beats none.
            if (result != null) {
                auctions = result;
                Features.recomputeAll();
            }
        }));
    }

    /** NPC prices and names practically never change, so they're fetched once; a failure is retried after 30s. */
    private static void loadNpcPrices() {
        if (fetchingNpcPrices) return;
        fetchingNpcPrices = true;
        lastNpcPriceAttemptMs = System.currentTimeMillis();

        Minecraft client = Minecraft.getInstance();
        CompletableFuture.supplyAsync(() -> {
            try {
                return ItemsApiClient.fetch();
            } catch (Exception e) {
                HypaFlippa.LOGGER.error("Failed to fetch the item list (NPC prices)", e);
                return null;
            }
        }).thenAccept(result -> client.execute(() -> {
            fetchingNpcPrices = false;
            if (result == null) return;
            HypaFlippa.LOGGER.info("Loaded NPC sell prices for {} items", result.npcPrices().size());
            npcPrices = result.npcPrices();
            itemNames = result.names();
            Features.recomputeAll();
        }));
    }

    /** Called every tick while the window is visible; refreshes whatever has gone stale. */
    public static void refreshIfStale() {
        long now = System.currentTimeMillis();
        if (npcPrices == null && now - lastNpcPriceAttemptMs >= 30_000) loadNpcPrices();
        if (now - lastFetchMs >= AUTO_REFRESH_MS) refresh();
        if (now - lastAuctionFetchMs >= AUCTION_REFRESH_MS) refreshAuctions();
    }
}
