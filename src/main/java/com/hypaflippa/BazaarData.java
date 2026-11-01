package com.hypaflippa;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;

public class BazaarData {
    private boolean success;
    private long lastUpdated;
    private Map<String, Product> products;

    public boolean isSuccess() { return success; }
    public long getLastUpdated() { return lastUpdated; }
    public Map<String, Product> getProducts() { return products; }

    public static class Product {
        private String product_id;
        private List<SummaryEntry> buy_summary;
        private List<SummaryEntry> sell_summary;
        private QuickStatus quick_status;

        public String getProductId() { return product_id; }

        // Hypixel names these lists from the other side of the trade, so the names look swapped:
        // "buy_summary" is what a buyer buys from (sell offers), "sell_summary" is what a seller
        // sells into (buy orders). The getters use what the lists actually contain.

        /** Sell offers, cheapest first. The first entry is the instant-buy price. */
        public List<SummaryEntry> getSellOffers() { return buy_summary; }
        /** Buy orders, highest first. The first entry is the instant-sell price. */
        public List<SummaryEntry> getBuyOrders() { return sell_summary; }
        public QuickStatus getQuickStatus() { return quick_status; }
    }

    public static class SummaryEntry {
        private int amount;
        @SerializedName("pricePerUnit")
        private double pricePerUnit;
        private int orders;

        public double getPricePerUnit() { return pricePerUnit; }
        public int getAmount() { return amount; }
        public int getOrders() { return orders; }
    }

    public static class QuickStatus {
        private String productId;
        private double sellPrice;
        private int sellVolume;
        @SerializedName("sellMovingWeek")
        private int sellMovingWeek;
        private double buyPrice;
        private int buyVolume;
        @SerializedName("buyMovingWeek")
        private int buyMovingWeek;

        public double getSellPrice() { return sellPrice; }
        public double getBuyPrice() { return buyPrice; }
        public String getProductId() { return productId; }
        public int getSellMovingWeek() { return sellMovingWeek; }
        public int getBuyMovingWeek() { return buyMovingWeek; }
    }
}
