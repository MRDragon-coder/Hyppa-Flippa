package com.hypaflippa.market.auction;

import java.util.Map;
import java.util.OptionalDouble;

/**
 * A snapshot of the Auction House: the cheapest Buy-It-Now price per unit for every item.
 *
 * Items are identified by price keys (see {@link AuctionApiClient#priceKey}): the SkyBlock item
 * id, plus type and level for runes ({@code RUNE:JERRY:3}) and type and rarity for pets
 * ({@code PET:SNOWMAN:LEGENDARY}).
 *
 * @param binCounts how many BIN auctions each item has: with only one or two, the lowest BIN is
 *                  just whatever someone asked, not a market price
 */
public record AuctionPrices(Map<String, Double> lowestBin, Map<String, Integer> binCounts, int binAuctions, long fetchedAtMs) {

    public OptionalDouble lowestBin(String key) {
        Double price = lowestBin.get(key);
        return price == null ? OptionalDouble.empty() : OptionalDouble.of(price);
    }

    /** BIN auctions of this item in the last scan, 0 if none. */
    public int binCount(String key) {
        return binCounts.getOrDefault(key, 0);
    }

    /** What you'd actually receive selling one at the lowest BIN, after Hypixel's auction fees. */
    public OptionalDouble sellValue(String key) {
        OptionalDouble price = lowestBin(key);
        return price.isPresent() ? OptionalDouble.of(afterFees(price.getAsDouble())) : price;
    }

    /**
     * Auction House fees: a listing fee of 1% (2% from 10M, 2.5% from 100M) plus a 1% claiming
     * tax on sales over 1M.
     */
    public static double afterFees(double price) {
        double listingFee = price >= 100_000_000 ? 0.025 : price >= 10_000_000 ? 0.02 : 0.01;
        double claimTax = price > 1_000_000 ? 0.01 : 0;
        return price * (1 - listingFee - claimTax);
    }
}
