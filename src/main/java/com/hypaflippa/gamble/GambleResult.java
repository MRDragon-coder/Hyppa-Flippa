package com.hypaflippa.gamble;

import java.util.List;
import java.util.OptionalDouble;

/**
 * The expected outcome of doing one gamble once.
 *
 * @param id         bazaar product id of what you buy (box, gift, fragment)
 * @param cost       coins spent per gamble, empty when it can't be bought on the bazaar right now
 * @param costDetail how the cost is made up, e.g. "32.11 × 10,256"
 * @param lines      where the average return comes from, biggest first
 */
public record GambleResult(String id, String name, OptionalDouble cost, String costDetail,
                           double expectedReturn, List<Line> lines) {

    public enum Kind {
        /** Counted in the return: coins or a sellable item. */
        VALUE,
        /** A sellable item we have no price for yet (auction items); counts as 0. */
        NO_PRICE,
        /** Can't be turned into coins (skill XP, North Stars): shown, not counted. */
        NOT_COUNTED,
        /** Extra explanation, no value of its own. */
        INFO
    }

    /** @param value average coins this line adds per gamble (only meaningful for VALUE) */
    public record Line(String label, String detail, double value, Kind kind) {}

    public boolean hasCost() {
        return cost.isPresent();
    }

    public double profit() {
        return expectedReturn - cost.orElse(0);
    }

    /** Profit as a percentage of the cost. */
    public double roi() {
        double c = cost.orElse(0);
        return c > 0 ? profit() / c * 100 : 0;
    }

    /** True when some drops have no price, so the real return is somewhat higher. */
    public boolean isIncomplete() {
        return lines.stream().anyMatch(line -> line.kind() == Kind.NO_PRICE);
    }
}
