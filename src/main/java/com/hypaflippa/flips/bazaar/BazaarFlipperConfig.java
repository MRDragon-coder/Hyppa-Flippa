package com.hypaflippa.flips.bazaar;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypaflippa.HypaFlippa;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Settings of the Bazaar tab (config/hypaflippa/bazaar.json): one filter set per page. Window
 * position/size are shared and live in {@link HypaFlippaConfig}.
 */
public class BazaarFlipperConfig {
    /** Sort columns of the Orders page (also what /flips accepts). */
    public static final List<String> ORDER_SORT_MODES = List.of("name", "buy", "sell", "margin", "vol", "outbid", "coins");
    /** Sort columns of the NPC page. */
    public static final List<String> NPC_SORT_MODES = List.of("name", "order", "insta", "npc", "orderMargin", "instaMargin", "vol", "outbid", "coins");

    public static final String PAGE_ORDERS = "orders";
    public static final String PAGE_NPC = "npc";

    /** Which page of the Bazaar tab is open. */
    public String page = PAGE_ORDERS;
    /** Buy order â†’ sell offer flips. */
    public FlipFilters orders = new FlipFilters("buy");
    /**
     * Buy order / insta-buy â†’ sell to an NPC. Starts sorted on insta margin: order margins are
     * often inflated by joke buy orders at ~0.1 coins.
     */
    public FlipFilters npc = new FlipFilters("instaMargin");
    /** Order-book manipulation threshold for the Orders page (see BazaarFlip.isManipulated). */
    public double maxSpreadRatio = 2.0;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    // Each feature area gets its own file in config/hypaflippa/.
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("hypaflippa").resolve("bazaar.json");
    // Where settings lived before the rename to Hypa Flippa; read once so nobody loses them.
    private static final Path LEGACY_PATH = FabricLoader.getInstance().getConfigDir().resolve("bazaaflipper.json");

    /** The saved bazaar settings file, falling back to the pre-rename one; null if neither exists. */
    public static Path existingFile() {
        if (Files.exists(PATH)) return PATH;
        return Files.exists(LEGACY_PATH) ? LEGACY_PATH : null;
    }

    public boolean isNpcPage() {
        return PAGE_NPC.equals(page);
    }

    public void load() {
        try {
            Path source = existingFile();
            if (source == null) return;
            JsonObject json = JsonParser.parseString(Files.readString(source)).getAsJsonObject();
            if (json.has("orders")) {
                BazaarFlipperConfig saved = GSON.fromJson(json, BazaarFlipperConfig.class);
                if (saved.page != null) page = saved.page;
                if (saved.orders != null) orders = saved.orders;
                if (saved.npc != null) npc = saved.npc;
                maxSpreadRatio = saved.maxSpreadRatio;
            } else {
                // Before the NPC page existed the order filters were stored flat; they become the Orders page.
                orders = GSON.fromJson(json, FlipFilters.class);
                if (json.has("maxSpreadRatio")) maxSpreadRatio = json.get("maxSpreadRatio").getAsDouble();
            }
            if (orders.sortBy == null || !ORDER_SORT_MODES.contains(orders.sortBy)) orders.sortBy = "buy";
            if (npc.sortBy == null || !NPC_SORT_MODES.contains(npc.sortBy)) npc.sortBy = "instaMargin";
        } catch (Exception e) {
            HypaFlippa.LOGGER.error("Failed to load config", e);
        }
    }

    public void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(this));
        } catch (IOException e) {
            HypaFlippa.LOGGER.error("Failed to save config", e);
        }
    }
}
