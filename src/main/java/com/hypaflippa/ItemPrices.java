package com.hypaflippa;

import com.hypaflippa.auction.AuctionPrices;

import java.util.Optional;
import java.util.OptionalDouble;

/** What an item is worth when you sell it: bazaar first, otherwise the Auction House. */
public final class ItemPrices {
    public enum Source { BAZAAR, AUCTION }

    public record Price(double value, Source source) {}

    private ItemPrices() {}

    /**
     * @param auctions may be null while the first Auction House scan is still running
     * @param key      a bazaar product id, or an auction price key such as {@code RUNE:JERRY:3}
     */
    public static Optional<Price> sellValue(BazaarData bazaar, AuctionPrices auctions, String key, BazaarPrices.Mode mode) {
        OptionalDouble bazaarPrice = BazaarPrices.sellPrice(bazaar, key, mode);
        if (bazaarPrice.isPresent()) return Optional.of(new Price(bazaarPrice.getAsDouble(), Source.BAZAAR));
        if (auctions != null) {
            OptionalDouble auctionPrice = auctions.sellValue(key);
            if (auctionPrice.isPresent()) return Optional.of(new Price(auctionPrice.getAsDouble(), Source.AUCTION));
        }
        return Optional.empty();
    }
}
