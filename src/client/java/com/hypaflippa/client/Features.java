package com.hypaflippa.client;

import com.hypaflippa.HypaFlippaConfig;
import com.hypaflippa.client.flips.BazaarFeature;
import com.hypaflippa.client.flips.CraftFeature;

import java.util.List;

/**
 * Every feature, and when they recompute. New market data recomputes all of them; a changed
 * setting or a newly arrived stat only the one it belongs to.
 */
public final class Features {
    /** Window and gamble-tab settings. */
    public static final HypaFlippaConfig general = new HypaFlippaConfig();
    public static final BazaarFeature bazaar = new BazaarFeature();
    public static final CraftFeature craft = new CraftFeature();

    private static final List<Feature> ALL = List.of(bazaar, craft);

    /** The feature of the open tab, or null (gamble tabs compute as they draw). */
    private static Feature shown;
    /** Price-history stats arrived since the last tick. */
    private static boolean statsArrived;

    private Features() {}

    public static void load() {
        general.load();
        ALL.forEach(Feature::load);
    }

    /** New market data: recompute everything, then fetch stats for what's on screen. */
    public static void recomputeAll() {
        ALL.forEach(Feature::recompute);
        if (shown != null) shown.requestStats();
    }

    /** The open tab changed; its feature recomputes so it shows the latest stats too. */
    public static void show(Feature feature) {
        shown = feature;
        if (feature != null) changed(feature);
    }

    /** A setting of this feature changed. */
    public static void changed(Feature feature) {
        feature.recompute();
        if (feature == shown) feature.requestStats();
    }

    /**
     * A price-history stat came in, which can change filters and sorting. Many arrive in a row, so
     * the open feature recomputes at most once per tick ({@link #tick}).
     */
    public static void statsArrived() {
        statsArrived = true;
    }

    /** Called every client tick. */
    public static void tick() {
        if (!statsArrived) return;
        statsArrived = false;
        if (shown != null) changed(shown);
    }
}
