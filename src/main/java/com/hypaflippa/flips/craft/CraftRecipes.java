package com.hypaflippa.flips.craft;

import com.google.gson.Gson;
import com.hypaflippa.HypaFlippa;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The crafting recipes bundled with the mod (assets/hypaflippa/recipes.json), taken from the
 * NotEnoughUpdates repo by {@code ./gradlew updateRecipes}. Items are named by the same price
 * keys as everywhere else: bazaar product ids, and {@code PET:NAME:RARITY} for pets.
 *
 * @param npcShop  the cheapest coin price per unit of items an NPC sells
 * @param names    display names of every item a recipe mentions, colour codes removed
 * @param sackable every item that fits in a sack
 */
public record CraftRecipes(String commit, String updated, List<Recipe> recipes, Map<String, Double> npcShop,
                           Map<String, String> names, Set<String> sackable) {
    private static final String RESOURCE = "/assets/hypaflippa/recipes.json";

    /** One way to craft {@code count} of an item. {@code requires} is the collection or slayer needed, or null. */
    public record Recipe(String id, int count, Map<String, Integer> inputs, String requires) {}

    private static final CraftRecipes EMPTY = new CraftRecipes("", "", List.of(), Map.of(), Map.of(), Set.of());
    private static CraftRecipes loaded;

    /** The bundled recipes, read on first use. Empty if the file is missing or broken. */
    public static CraftRecipes get() {
        if (loaded == null) {
            try (InputStream in = CraftRecipes.class.getResourceAsStream(RESOURCE)) {
                if (in == null) throw new IllegalStateException(RESOURCE + " is missing");
                CraftRecipes parsed = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), CraftRecipes.class);
                loaded = new CraftRecipes(parsed.commit(), parsed.updated(), List.copyOf(parsed.recipes()),
                        Map.copyOf(parsed.npcShop()), Map.copyOf(parsed.names()),
                        parsed.sackable() == null ? Set.of() : Set.copyOf(parsed.sackable()));
                HypaFlippa.LOGGER.info("Loaded {} crafting recipes (NEU {}, {})", loaded.recipes().size(),
                        loaded.commit().substring(0, Math.min(7, loaded.commit().length())), loaded.updated());
            } catch (Exception e) {
                HypaFlippa.LOGGER.error("Failed to load the bundled crafting recipes", e);
                loaded = EMPTY;
            }
        }
        return loaded;
    }
}
