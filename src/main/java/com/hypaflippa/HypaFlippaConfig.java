package com.hypaflippa;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypaflippa.flips.bazaar.BazaarFlipperConfig;
import com.hypaflippa.market.bazaar.BazaarPrices;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Settings shared by the whole Hypa Flippa window (config/hypaflippa/general.json). */
public class HypaFlippaConfig {
    public int windowX = 10;
    public int windowY = 10;
    public int windowWidth = 300;
    public int windowHeight = 400;
    /** Id of the tab that was open last. */
    public String activeTab = "bazaar";
    /** Per section of the window (Flippa, Gamba): the id of the tab that was open last in it. */
    public Map<String, String> lastTabs = new HashMap<>();

    /** How the gamble tabs price things (the flipper always uses order prices). */
    public BazaarPrices.Mode priceMode = BazaarPrices.Mode.ORDER;
    /** Mythic Endermite pet level, 0 = none. Raises the Draconic Altar bonus chance. */
    public int endermiteLevel = 0;
    /** Gifts: also count the reward of the other player (both get one roll). */
    public boolean giftBothRolls = false;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("hypaflippa").resolve("general.json");

    public void load() {
        try {
            if (Files.exists(PATH)) {
                HypaFlippaConfig saved = GSON.fromJson(Files.readString(PATH), HypaFlippaConfig.class);
                if (saved != null) copyFrom(saved);
            } else {
                migrateWindowFromBazaarConfig();
            }
        } catch (Exception e) {
            HypaFlippa.LOGGER.error("Failed to load config", e);
        }
    }

    public void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(this));
        } catch (Exception e) {
            HypaFlippa.LOGGER.error("Failed to save config", e);
        }
    }

    private void copyFrom(HypaFlippaConfig saved) {
        windowX = saved.windowX;
        windowY = saved.windowY;
        windowWidth = saved.windowWidth;
        windowHeight = saved.windowHeight;
        if (saved.activeTab != null) activeTab = saved.activeTab;
        if (saved.lastTabs != null) lastTabs = new HashMap<>(saved.lastTabs);
        if (saved.priceMode != null) priceMode = saved.priceMode;
        endermiteLevel = saved.endermiteLevel;
        giftBothRolls = saved.giftBothRolls;
    }

    /** Before there were tabs, the window position was stored with the bazaar settings. */
    private void migrateWindowFromBazaarConfig() throws Exception {
        Path old = BazaarFlipperConfig.existingFile();
        if (old == null) return;
        JsonObject json = JsonParser.parseString(Files.readString(old)).getAsJsonObject();
        if (json.has("x")) windowX = json.get("x").getAsInt();
        if (json.has("y")) windowY = json.get("y").getAsInt();
        if (json.has("width")) windowWidth = json.get("width").getAsInt();
        if (json.has("height")) windowHeight = json.get("height").getAsInt();
    }
}
