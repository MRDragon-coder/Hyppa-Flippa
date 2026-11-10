package com.hypaflippa.flips.bazaar;

import com.hypaflippa.SortSettings;

import java.util.Comparator;
import java.util.function.Function;

/** The filters and sort order of one flip list (the Orders page or the NPC page of the Bazaar tab). */
public class FlipFilters extends SortSettings {
    public double minProfitPercent = 1.0;
    /** NPC page only: minimum insta-buy margin, or null when that filter is off. */
    public Double minInstaProfitPercent = null;
    public int maxResults = 10;
    public int minSellMovingWeek = 0;
    public int minBuyMovingWeek = 0;
    /** Hide items whose price moved more than ± this % over the last day; null = off. */
    public Double maxVolatilityPercent = null;
    /** Hide items whose orders get beaten more often than once per this many minutes; null = off. */
    public Double minOutbidMinutes = null;
    /** Hide items with fewer coins per hour flowing through them than this; null = off. */
    public Double minCoinsPerHour = null;

    public FlipFilters() {}

    public FlipFilters(String sortBy) {
        this.sortBy = sortBy;
    }

    /**
     * The direction a column starts in when first picked: names A-Z, volatility calmest first,
     * other numbers highest first (for outbid time: the slowest, most relaxed items first).
     */
    public static boolean defaultAscending(String sortMode) {
        return sortMode.equals("name") || sortMode.equals("vol");
    }

    @Override
    public boolean startsAscending(String sortMode) {
        return defaultAscending(sortMode);
    }

    /**
     * Whether the history-based filters hide this item. Values that aren't known yet (null) never
     * hide anything.
     *
     * @param outbidMinutes minutes between outbids for the side(s) that matter on this page
     */
    public boolean hiddenByStats(Double volatilityPercent, Double outbidMinutes) {
        if (maxVolatilityPercent != null && volatilityPercent != null && volatilityPercent > maxVolatilityPercent) return true;
        return minOutbidMinutes != null && outbidMinutes != null && outbidMinutes < minOutbidMinutes;
    }

    /** Sorts by a history-based stat in the chosen direction, with items whose value isn't known yet always last. */
    public <T> Comparator<T> statOrder(Function<T, Double> stat) {
        Comparator<Double> byValue = sortAscending ? Comparator.naturalOrder() : Comparator.reverseOrder();
        return Comparator.comparing(stat, Comparator.nullsLast(byValue));
    }
}
