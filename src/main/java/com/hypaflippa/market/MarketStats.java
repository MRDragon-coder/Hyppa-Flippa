package com.hypaflippa.market;

/**
 * Per-item statistics that come from price history rather than the current order book (see
 * {@link CoflnetApiClient}). Both return null while a value isn't known yet; unknown items are
 * never filtered out and sort last.
 */
public interface MarketStats {
    /** How much the price moved over the last day, in ± percent. */
    Double volatilityPercent(String productId);

    /** How often top orders got beaten during the last hour. */
    CoflnetApiClient.Outbid outbid(String productId);

    /** How many of an Auction House item sold per day recently. */
    Double auctionSalesPerDay(String itemId);

    /** Nothing known about anything (tests, or before the cache exists). */
    MarketStats NONE = new MarketStats() {
        @Override public Double volatilityPercent(String productId) { return null; }
        @Override public CoflnetApiClient.Outbid outbid(String productId) { return null; }
        @Override public Double auctionSalesPerDay(String itemId) { return null; }
    };
}
