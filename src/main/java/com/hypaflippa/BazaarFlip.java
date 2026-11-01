package com.hypaflippa;

/** One order flip: buy with a buy order at buyPrice, sell with a sell offer at sellPrice. */
public class BazaarFlip {
    private final String productId;
    private final double buyPrice;
    private final double sellPrice;
    private final int maxQuantity;
    private final double profitPerUnit;
    private final double profitPercent;
    private final int sellMovingWeek;
    private final int buyMovingWeek;

    public BazaarFlip(String productId, double buyPrice, double sellPrice, int maxQuantity, int sellMovingWeek, int buyMovingWeek) {
        this.productId = productId;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.maxQuantity = maxQuantity;
        this.profitPerUnit = BazaarPrices.afterTax(sellPrice) - buyPrice;
        this.profitPercent = buyPrice > 0 ? (profitPerUnit / buyPrice) * 100 : 0;
        this.sellMovingWeek = sellMovingWeek;
        this.buyMovingWeek = buyMovingWeek;
    }

    public String getProductId() { return productId; }
    public double getBuyPrice() { return buyPrice; }
    public double getSellPrice() { return sellPrice; }
    public int getMaxQuantity() { return maxQuantity; }
    public double getProfitPerUnit() { return profitPerUnit; }
    public double getProfitPercent() { return profitPercent; }
    public int getSellMovingWeek() { return sellMovingWeek; }
    public int getBuyMovingWeek() { return buyMovingWeek; }
}
