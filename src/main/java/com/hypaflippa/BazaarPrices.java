package com.hypaflippa;

import java.util.List;
import java.util.OptionalDouble;

/**
 * The one place that turns the bazaar order book into "what does this cost me" and "what do I get
 * for it". Everything that values items (flipper, gamble calculators) should go through here.
 */
public final class BazaarPrices {
    /** Bazaar tax taken from the coins you receive when selling. */
    public static final double SELL_TAX = 0.015;

    public enum Mode {
        /** Patient: buy with a buy order at the top buy-order price, sell with a sell offer at the lowest sell-offer price. */
        ORDER,
        /** Immediate: instant-buy from the cheapest sell offer, instant-sell into the highest buy order. */
        INSTANT
    }

    private BazaarPrices() {}

    /** Coins paid for one unit, or empty if the item isn't on the bazaar or that side of the book is empty. */
    public static OptionalDouble buyPrice(BazaarData data, String productId, Mode mode) {
        BazaarData.Product product = product(data, productId);
        if (product == null) return OptionalDouble.empty();
        return firstPrice(mode == Mode.ORDER ? product.getBuyOrders() : product.getSellOffers());
    }

    /** Coins received for one unit after tax, or empty if it can't be sold on the bazaar right now. */
    public static OptionalDouble sellPrice(BazaarData data, String productId, Mode mode) {
        BazaarData.Product product = product(data, productId);
        if (product == null) return OptionalDouble.empty();
        OptionalDouble price = firstPrice(mode == Mode.ORDER ? product.getSellOffers() : product.getBuyOrders());
        return price.isPresent() ? OptionalDouble.of(afterTax(price.getAsDouble())) : price;
    }

    public static double afterTax(double price) {
        return price * (1 - SELL_TAX);
    }

    private static BazaarData.Product product(BazaarData data, String productId) {
        return data == null || data.getProducts() == null ? null : data.getProducts().get(productId);
    }

    private static OptionalDouble firstPrice(List<BazaarData.SummaryEntry> entries) {
        return entries == null || entries.isEmpty() ? OptionalDouble.empty() : OptionalDouble.of(entries.get(0).getPricePerUnit());
    }
}
