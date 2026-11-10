package com.hypaflippa.flips.craft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypaflippa.HypaFlippa;
import com.hypaflippa.SortSettings;
import com.hypaflippa.market.bazaar.BazaarPrices;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Settings of the Craft tab (config/hypaflippa/craft.json). */
public class CraftConfig extends SortSettings {
    /** Sort columns of the Craft tab. */
    public static final List<String> SORT_MODES = List.of("name", "cost", "sell", "profit", "margin", "coins", "buyable", "sales", "listed", "vol", "outbid", "requires");

    /** How ingredients are bought on the bazaar (Auction House ingredients are always the lowest BIN). */
    public BazaarPrices.Mode buyMode = BazaarPrices.Mode.ORDER;
    /** How the crafted item is sold on the bazaar (on the Auction House: at the lowest BIN). */
    public BazaarPrices.Mode sellMode = BazaarPrices.Mode.ORDER;
    /** Also list crafts whose result only sells on the Auction House. */
    public boolean includeAuction = true;
    /** Let ingredients be bought from NPC shops when that's cheapest. */
    public boolean allowNpcShop = true;
    /** Only list recipes whose ingredients all fit in sacks, so any amount can be crafted straight from them. */
    public boolean sacksOnly = false;
    public double minProfit = 10_000;
    public double minMarginPercent = 5;
    /** Hide results that sell fewer than this many a day (results whose sales aren't known yet stay). */
    public int minSalesPerDay = 0;
    /**
     * Hide Auction House results with fewer BIN listings than this: with only one or two, the
     * lowest BIN is whatever someone asked rather than a price anything sells at.
     */
    public int minListed = 5;
    public int maxResults = 25;

    public CraftConfig() {
        sortBy = "profit";
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("hypaflippa").resolve("craft.json");
    }

    /** Names and requirements A-Z first, volatility calmest first, other numbers highest first. */
    public static boolean defaultAscending(String sortMode) {
        return sortMode.equals("name") || sortMode.equals("requires") || sortMode.equals("vol");
    }

    @Override
    public boolean startsAscending(String sortMode) {
        return defaultAscending(sortMode);
    }

    public void load() {
        try {
            if (!Files.exists(path())) return;
            CraftConfig saved = GSON.fromJson(Files.readString(path()), CraftConfig.class);
            if (saved == null) return;
            if (saved.buyMode != null) buyMode = saved.buyMode;
            if (saved.sellMode != null) sellMode = saved.sellMode;
            includeAuction = saved.includeAuction;
            allowNpcShop = saved.allowNpcShop;
            sacksOnly = saved.sacksOnly;
            minProfit = saved.minProfit;
            minMarginPercent = saved.minMarginPercent;
            minSalesPerDay = saved.minSalesPerDay;
            minListed = saved.minListed;
            maxResults = Math.max(1, saved.maxResults);
            if (saved.sortBy != null && SORT_MODES.contains(saved.sortBy)) sortBy = saved.sortBy;
            sortAscending = saved.sortAscending;
            if (saved.hiddenColumns != null) hiddenColumns = saved.hiddenColumns;
        } catch (Exception e) {
            HypaFlippa.LOGGER.error("Failed to load craft config", e);
        }
    }

    public void save() {
        try {
            Files.createDirectories(path().getParent());
            Files.writeString(path(), GSON.toJson(this));
        } catch (Exception e) {
            HypaFlippa.LOGGER.error("Failed to save craft config", e);
        }
    }
}
