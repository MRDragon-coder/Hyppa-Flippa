package com.hypaflippa.gamble;

import com.hypaflippa.Format;
import com.hypaflippa.gamble.GambleResult.Kind;
import com.hypaflippa.gamble.GambleResult.Line;
import com.hypaflippa.market.ItemPrices;
import com.hypaflippa.market.auction.AuctionPrices;
import com.hypaflippa.market.bazaar.BazaarData;
import com.hypaflippa.market.bazaar.BazaarPrices;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/** Expected value of opening a weighted-table gamble (Jerry Boxes, gifts) once. */
public final class GambleCalculator {
    private GambleCalculator() {}

    /**
     * @param auctions Auction House prices for drops that aren't on the bazaar; may be null
     * @param rolls    how many rolls one purchase gives you. 1 normally; 2 for gifts when counting
     *                 both the giver's and the receiver's reward.
     */
    public static GambleResult evaluate(GambleData.Table table, BazaarData data, AuctionPrices auctions,
                                        BazaarPrices.Mode mode, int rolls) {
        OptionalDouble cost = BazaarPrices.buyPrice(data, table.id, mode);
        String costDetail = cost.isPresent() ? "1 × " + Format.exactCoins(cost.getAsDouble()) : "not on the bazaar right now";

        double totalWeight = table.totalWeight();
        List<Line> lines = new ArrayList<>();
        double expectedReturn = 0;

        for (GambleData.Drop drop : table.drops) {
            double chance = drop.weight / totalWeight;
            String detail = rolls > 1 ? rolls + " × " + Format.chance(chance) : Format.chance(chance);

            if (drop.isCoins()) {
                double value = rolls * chance * drop.coins;
                expectedReturn += value;
                lines.add(new Line(drop.label, detail, value, Kind.VALUE));
            } else if (drop.isXp() || drop.noMarket) {
                lines.add(new Line(drop.label, detail, 0, Kind.NOT_COUNTED));
            } else {
                Optional<ItemPrices.Price> price = drop.item == null ? Optional.empty()
                        : ItemPrices.sellValue(data, auctions, drop.item, mode);
                if (price.isPresent()) {
                    double each = drop.count * price.get().value();
                    double value = rolls * chance * each;
                    expectedReturn += value;
                    String source = price.get().source() == ItemPrices.Source.AUCTION ? " AH" : "";
                    lines.add(new Line(drop.label, detail + " × " + Format.coins(each) + source, value, Kind.VALUE));
                } else {
                    lines.add(new Line(drop.label, detail, 0, Kind.NO_PRICE));
                }
            }
        }
        for (GambleData.Extra extra : table.extras) {
            String detail = Format.chance(extra.chance) + ", avg " + trimAmount(rolls * extra.chance * extra.avgAmount);
            lines.add(new Line(extra.label, detail, 0, Kind.NOT_COUNTED));
        }

        // Biggest contributions first, then unpriced items, then things that aren't counted.
        lines.sort(Comparator.comparingInt((Line line) -> line.kind().ordinal())
                .thenComparing(Comparator.comparingDouble(Line::value).reversed()));
        return new GambleResult(table.id, table.name, cost, costDetail, expectedReturn, lines);
    }

    private static String trimAmount(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}
