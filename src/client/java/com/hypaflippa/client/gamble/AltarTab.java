package com.hypaflippa.client.gamble;

import com.hypaflippa.Format;
import com.hypaflippa.HypaFlippaConfig;
import com.hypaflippa.client.Features;
import com.hypaflippa.client.MarketState;
import com.hypaflippa.client.ui.Controls;
import com.hypaflippa.client.ui.Stepper;
import com.hypaflippa.client.ui.Tab;
import com.hypaflippa.client.ui.Ui;
import com.hypaflippa.gamble.AltarCalculator;
import com.hypaflippa.gamble.GambleData;
import com.hypaflippa.gamble.GambleResult;
import com.hypaflippa.market.bazaar.BazaarData;
import com.hypaflippa.market.bazaar.BazaarPrices;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/**
 * Draconic Altar: the most profitable fragment to turn into Dragon Boots and sacrifice, with its
 * breakdown. On a fragment's bazaar page it shows that fragment instead.
 */
public class AltarTab implements Tab {
    private static final int HEADLINE_HEIGHT = 26;

    private final Controls controls = new Controls(1, () -> config().save())
            .button(0, 4, 92, () -> config().priceMode == BazaarPrices.Mode.ORDER ? "Prices: Order" : "Prices: Instant", () -> false,
                    () -> new Ui.Help("Prices",
                            "Order: buy fragments with buy orders and sell drops with sell offers (better prices, but you wait).\nInstant: insta-buy and insta-sell."),
                    () -> config().priceMode = config().priceMode == BazaarPrices.Mode.ORDER ? BazaarPrices.Mode.INSTANT : BazaarPrices.Mode.ORDER)
            .stepper(0, 100, new Stepper(76, () -> "Endermite " + config().endermiteLevel, (dir, shift) -> {
                HypaFlippaConfig c = config();
                c.endermiteLevel = Math.clamp(c.endermiteLevel + dir * (shift ? 10 : 1), 0, 100);
            }), () -> new Ui.Help("Mythic Endermite level",
                    "The level of your Mythic Endermite pet, which raises the altar's bonus chance. 0 if you don't have one."));
    private final BreakdownView breakdown = new BreakdownView();

    /** Fragment item id picked by a bazaar page, or null to show the best one. */
    private String focused;

    // The last results and what they were worked out from: they only change with these.
    private List<GambleResult> results;
    private BazaarData resultsData;
    private BazaarPrices.Mode resultsMode;
    private int resultsEndermite;

    private static HypaFlippaConfig config() {
        return Features.general;
    }

    private List<GambleResult> results(BazaarData data) {
        HypaFlippaConfig c = config();
        if (results == null || data != resultsData || c.priceMode != resultsMode || c.endermiteLevel != resultsEndermite) {
            results = AltarCalculator.evaluateAll(GambleData.altar(), data, c.priceMode, c.endermiteLevel);
            resultsData = data;
            resultsMode = c.priceMode;
            resultsEndermite = c.endermiteLevel;
        }
        return results;
    }

    @Override public String id() { return "altar"; }
    @Override public String title() { return "Altar"; }

    // --- Layout

    private Ui.Rect breakdownArea(Ui.Rect area) {
        int top = area.y() + controls.height() + HEADLINE_HEIGHT;
        return new Ui.Rect(area.x(), top, area.w(), area.bottom() - top);
    }

    // --- Input

    @Override
    public void mouseClicked(Ui.Rect area, double mx, double my, boolean shift) {
        controls.click(area, mx, my, shift);
    }

    @Override
    public void mouseScrolled(Ui.Rect area, double mx, double my, double deltaY) {
        if (breakdownArea(area).contains(mx, my)) breakdown.mouseScrolled(deltaY);
    }

    @Override
    public boolean focusBazaarPage(String title) {
        for (GambleData.Fragment fragment : GambleData.altar().fragments) {
            if (Ui.titleShowsItem(title, fragment.name)) {
                if (!fragment.item.equals(focused)) breakdown.resetScroll();
                focused = fragment.item;
                return true;
            }
        }
        return false;
    }

    @Override
    public void clearFocus() {
        focused = null;
        breakdown.resetScroll();
    }

    // --- Rendering

    @Override
    public void render(GuiGraphicsExtractor guis, Font font, Ui.Rect area, int mouseX, int mouseY) {
        controls.render(guis, font, area, mouseX, mouseY);
        Ui.Help help = controls.helpAt(area, mouseX, mouseY);
        if (help != null) Ui.helpTooltip(guis, font, area, mouseX, mouseY, help);

        BazaarData data = MarketState.data();
        if (data == null) {
            String message = MarketState.error() != null ? MarketState.error() : "Loading...";
            guis.centeredText(font, message, area.x() + area.w() / 2, area.y() + area.h() / 2, Ui.TEXT_FAINT);
            return;
        }

        List<GambleResult> results = results(data);
        GambleResult shown = results.getFirst();
        boolean isBest = true;
        if (focused != null) {
            for (GambleResult result : results) {
                if (result.id().equals(focused)) {
                    isBest = result == results.getFirst();
                    shown = result;
                }
            }
        }

        // Headline: which fragment, and its profit per sacrifice.
        int y = area.y() + controls.height() + 3;
        String name = (focused == null ? "Best: " : "") + shown.name();
        String profit = shown.hasCost() ? Format.signedCoins(shown.profit()) : "?";
        int profitColor = shown.hasCost() ? Ui.signColor(shown.profit()) : Ui.TEXT_FAINT;
        guis.text(font, Ui.fit(font, name, area.w() - 16 - font.width(profit)), area.x() + 4, y, Ui.ACCENT);
        Ui.textRight(guis, font, profit, area.right() - 4, y, profitColor);
        String subtitle = "per sacrifice of Dragon Boots (40 fragments)"
                + (focused != null && !isBest ? ", best is " + results.getFirst().name() : "");
        guis.text(font, Ui.fit(font, subtitle, area.w() - 8), area.x() + 4, y + 11, Ui.TEXT_FAINT);

        breakdown.render(guis, font, breakdownArea(area), shown);
    }
}
