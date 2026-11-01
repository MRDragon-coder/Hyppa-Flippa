package com.hypaflippa.gamble;

import com.google.gson.Gson;
import com.hypaflippa.HypaFlippa;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The drop tables bundled with the mod (assets/hypaflippa/gambles/*.json), copied from the
 * Hypixel SkyBlock wiki. Loaded once; to update chances, edit the JSON files.
 */
public final class GambleData {
    private static final Gson GSON = new Gson();
    private static final String DIR = "/assets/hypaflippa/gambles/";

    /** One possible outcome of a weighted roll. Exactly one of coins / xp / item is meaningful. */
    public static final class Drop {
        public String label;
        public double weight;
        public Double coins;
        public Double xp;
        /** Bazaar product id or auction price key (RUNE:TYPE:LEVEL, PET:TYPE:RARITY); null if unknown. */
        public String item;
        public int count = 1;
        /** Tradeable in theory but nobody buys it (e.g. skill-specific XP potions): shown, not counted. */
        public boolean noMarket;

        public boolean isCoins() { return coins != null; }
        public boolean isXp() { return xp != null; }
    }

    /** A separate roll on top of the table (e.g. North Stars), never sold so never counted. */
    public static final class Extra {
        public String label;
        public double chance;
        public double avgAmount;
    }

    /** One gamble: the item you buy (id, a bazaar product) and what opening it can give. */
    public static final class Table {
        public String id;
        public String name;
        public List<Drop> drops = new ArrayList<>();
        public List<Extra> extras = new ArrayList<>();

        public double totalWeight() {
            return drops.stream().mapToDouble(d -> d.weight).sum();
        }
    }

    public static final class Altar {
        public int fragmentsPerBoots;
        public double baseBonusChance;
        public double bonusChancePerPetLevel;
        public BonusFragments bonusFragments;
        public List<Drop> bonusItems = new ArrayList<>();
        public String essenceItem;
        public List<Fragment> fragments = new ArrayList<>();

        public double totalBonusWeight() {
            return bonusFragments.weight + bonusItems.stream().mapToDouble(d -> d.weight).sum();
        }
    }

    public static final class BonusFragments {
        public double weight;
        public int min;
        public int max;
    }

    public static final class Fragment {
        public String item;
        public String name;
        public int bootsEssence;
    }

    private static final class TableFile {
        List<Table> tables = new ArrayList<>();
    }

    private static List<Table> jerryBoxes;
    private static List<Table> gifts;
    private static Altar altar;

    private GambleData() {}

    public static List<Table> jerryBoxes() {
        if (jerryBoxes == null) jerryBoxes = load("jerry_boxes.json", TableFile.class).tables;
        return jerryBoxes;
    }

    public static List<Table> gifts() {
        if (gifts == null) gifts = load("gifts.json", TableFile.class).tables;
        return gifts;
    }

    public static Altar altar() {
        if (altar == null) altar = load("draconic_altar.json", Altar.class);
        return altar;
    }

    private static <T> T load(String file, Class<T> type) {
        try (InputStream in = GambleData.class.getResourceAsStream(DIR + file)) {
            if (in == null) throw new IllegalStateException("missing " + DIR + file);
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return GSON.fromJson(reader, type);
            }
        } catch (Exception e) {
            // A broken data file is a bug in the mod itself; fail loudly in the log, not silently.
            HypaFlippa.LOGGER.error("Failed to load gamble data {}", file, e);
            throw new IllegalStateException("Failed to load " + file, e);
        }
    }
}
