package com.hypaflippa.client;

/**
 * One area of the mod with its own settings and results, such as a kind of flip. Features
 * compute their results from {@link MarketState} and keep them until the next recompute, so tabs
 * only draw what's already there. Register new ones in {@link Features}.
 */
public interface Feature {
    /** Reads the settings from disk. Called once at startup. */
    void load();

    /** Recomputes the results from the latest market data and the settings. No network involved. */
    void recompute();

    /**
     * Asks for the price-history stats (Coflnet) that the results on screen use. Called while the
     * feature's tab is open, after every recompute: Coflnet's rate limit only allows fetching for
     * what's actually shown.
     */
    default void requestStats() {}
}
