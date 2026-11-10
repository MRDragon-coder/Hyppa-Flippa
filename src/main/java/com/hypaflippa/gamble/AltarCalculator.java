package com.hypaflippa.gamble;

import com.hypaflippa.Format;
import com.hypaflippa.gamble.GambleResult.Kind;
import com.hypaflippa.gamble.GambleResult.Line;
import com.hypaflippa.market.bazaar.BazaarData;
import com.hypaflippa.market.bazaar.BazaarPrices;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;

/**
 * Draconic Altar, per sacrifice of one pair of Dragon Boots crafted from fragments.
 *
 * The bonus fragments you get back are crafted into the next boots, so they're valued at what
 * they save you: over many sacrifices you only buy (40 - average fragments back) per sacrifice.
 * Everything else you get (essence, Ritual Residue, Summoning Eye, Dragon Horn) is sold.
 */
public final class AltarCalculator {
    private AltarCalculator() {}

    public static double bonusChance(GambleData.Altar altar, int endermiteLevel) {
        return Math.min(1, altar.baseBonusChance + altar.bonusChancePerPetLevel * endermiteLevel);
    }

    public static double averageFragmentsBack(GambleData.Altar altar, int endermiteLevel) {
        double fragmentShare = altar.bonusFragments.weight / altar.totalBonusWeight();
        double averageStack = (altar.bonusFragments.min + altar.bonusFragments.max) / 2.0;
        return bonusChance(altar, endermiteLevel) * fragmentShare * averageStack;
    }

    /** One result per fragment type, best profit first. */
    public static List<GambleResult> evaluateAll(GambleData.Altar altar, BazaarData data, BazaarPrices.Mode mode, int endermiteLevel) {
        List<GambleResult> results = new ArrayList<>();
        for (GambleData.Fragment fragment : altar.fragments) {
            results.add(evaluate(altar, fragment, data, mode, endermiteLevel));
        }
        // Results without a fragment price can't be ranked; keep them last.
        results.sort(Comparator.comparing((GambleResult r) -> !r.hasCost())
                .thenComparing(Comparator.comparingDouble(GambleResult::profit).reversed()));
        return results;
    }

    public static GambleResult evaluate(GambleData.Altar altar, GambleData.Fragment fragment, BazaarData data,
                                        BazaarPrices.Mode mode, int endermiteLevel) {
        double bonusChance = bonusChance(altar, endermiteLevel);
        double fragmentsBack = averageFragmentsBack(altar, endermiteLevel);
        double fragmentsBought = altar.fragmentsPerBoots - fragmentsBack;

        OptionalDouble fragmentPrice = BazaarPrices.buyPrice(data, fragment.item, mode);
        OptionalDouble cost = fragmentPrice.isPresent()
                ? OptionalDouble.of(fragmentsBought * fragmentPrice.getAsDouble()) : OptionalDouble.empty();
        String costDetail = fragmentPrice.isPresent()
                ? String.format(Locale.ROOT, "%.2f × %s", fragmentsBought, Format.exactCoins(fragmentPrice.getAsDouble()))
                : "not on the bazaar right now";

        List<Line> lines = new ArrayList<>();
        double expectedReturn = 0;

        OptionalDouble essencePrice = BazaarPrices.sellPrice(data, altar.essenceItem, mode);
        String essenceLabel = fragment.bootsEssence + " Dragon Essence";
        if (essencePrice.isPresent()) {
            double value = fragment.bootsEssence * essencePrice.getAsDouble();
            expectedReturn += value;
            lines.add(new Line(essenceLabel, "100% × " + Format.coins(essencePrice.getAsDouble()), value, Kind.VALUE));
        } else {
            lines.add(new Line(essenceLabel, "100%", 0, Kind.NO_PRICE));
        }

        double totalWeight = altar.totalBonusWeight();
        for (GambleData.Drop bonus : altar.bonusItems) {
            double chance = bonusChance * bonus.weight / totalWeight;
            OptionalDouble price = BazaarPrices.sellPrice(data, bonus.item, mode);
            if (price.isPresent()) {
                double value = chance * price.getAsDouble();
                expectedReturn += value;
                lines.add(new Line(bonus.label, Format.chance(chance) + " × " + Format.coins(price.getAsDouble()), value, Kind.VALUE));
            } else {
                lines.add(new Line(bonus.label, Format.chance(chance), 0, Kind.NO_PRICE));
            }
        }

        lines.sort(Comparator.comparingInt((Line line) -> line.kind().ordinal())
                .thenComparing(Comparator.comparingDouble(Line::value).reversed()));
        lines.add(new Line("Fragments back",
                String.format(Locale.ROOT, "avg %.2f, reused (in cost)", fragmentsBack), 0, Kind.INFO));
        lines.add(new Line("Bonus chance", Format.chance(bonusChance)
                + (endermiteLevel > 0 ? " (Endermite " + endermiteLevel + ")" : ""), 0, Kind.INFO));

        return new GambleResult(fragment.item, fragment.name, cost, costDetail, expectedReturn, lines);
    }
}
