package com.hypaflippa;

/** The filters and sort order of one flip list (the Orders page or the NPC page of the Bazaar tab). */
public class FlipFilters {
    public double minProfitPercent = 1.0;
    /** NPC page only: minimum insta-buy margin, or null when that filter is off. */
    public Double minInstaProfitPercent = null;
    public int maxResults = 10;
    public int minSellMovingWeek = 0;
    public int minBuyMovingWeek = 0;
    public String sortBy;
    public boolean sortAscending = false;

    public FlipFilters() {}

    public FlipFilters(String sortBy) {
        this.sortBy = sortBy;
    }

    /** The direction a column starts in when first picked: names A-Z, numbers highest first. */
    public static boolean defaultAscending(String sortMode) {
        return sortMode.equals("name");
    }
}
