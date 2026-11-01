package com.hypaflippa.client.tabs;

import com.hypaflippa.BazaarData;
import com.hypaflippa.BazaarPrices;
import com.hypaflippa.Format;
import com.hypaflippa.HypaFlippaConfig;
import com.hypaflippa.client.FlipperState;
import com.hypaflippa.client.ui.Stepper;
import com.hypaflippa.client.ui.Tab;
import com.hypaflippa.client.ui.Ui;
import com.hypaflippa.gamble.AltarCalculator;
import com.hypaflippa.gamble.GambleData;
import com.hypaflippa.gamble.GambleResult;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/**
 * Draconic Altar: the most profitable fragment to turn into Dragon Boots and sacrifice, with its
 * breakdown. On a fragment's bazaar page it shows that fragment instead.
 */
public class AltarTab implements Tab {
    private static final int CONTROLS_HEIGHT = 22;
    private static final int HEADLINE_HEIGHT = 26;

    private final BreakdownView breakdown = new BreakdownView();
    private final Stepper endermite = new Stepper(76, () -> "Endermite " + config().endermiteLevel, (dir, shift) -> {
        HypaFlippaConfig c = config();
        c.endermiteLevel = Math.clamp(c.endermiteLevel + dir * (shift ? 10 : 1), 0, 100);
    });

    /** Fragment item id picked by a bazaar page, or null to show the best one. */
    private String focused;

    private static HypaFlippaConfig config() {
        return FlipperState.general;
    }

    @Override public String id() { return "altar"; }
    @Override public String title() { return "Altar"; }

    // --- Layout

    private static Ui.Rect priceModeButton(Ui.Rect area) { return new Ui.Rect(area.x() + 4, area.y() + 3, 92, 16); }
    private static int stepperX(Ui.Rect area) { return area.x() + 100; }
    private static int stepperY(Ui.Rect area) { return area.y() + 3; }

    private static Ui.Rect breakdownArea(Ui.Rect area) {
        int top = area.y() + CONTROLS_HEIGHT + HEADLINE_HEIGHT;
        return new Ui.Rect(area.x(), top, area.w(), area.bottom() - top);
    }

    // --- Input

    @Override
    public void mouseClicked(Ui.Rect area, double mx, double my, boolean shift) {
        HypaFlippaConfig c = config();
        if (priceModeButton(area).contains(mx, my)) {
            c.priceMode = c.priceMode == BazaarPrices.Mode.ORDER ? BazaarPrices.Mode.INSTANT : BazaarPrices.Mode.ORDER;
            c.save();
        } else if (endermite.click(stepperX(area), stepperY(area), mx, my, shift)) {
            c.save();
        }
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
        HypaFlippaConfig c = config();
        guis.fill(area.x() - 2, area.y(), area.right() + 2, area.y() + CONTROLS_HEIGHT, Ui.CONTROLS_BG);
        Ui.button(guis, font, priceModeButton(area), c.priceMode == BazaarPrices.Mode.ORDER ? "Prices: Order" : "Prices: Instant", mouseX, mouseY);
        endermite.render(guis, font, stepperX(area), stepperY(area), mouseX, mouseY);

        BazaarData data = FlipperState.data();
        if (data == null) {
            String message = FlipperState.error() != null ? FlipperState.error() : "Loading...";
            guis.centeredText(font, message, area.x() + area.w() / 2, area.y() + area.h() / 2, Ui.TEXT_FAINT);
            return;
        }

        List<GambleResult> results = AltarCalculator.evaluateAll(GambleData.altar(), data, c.priceMode, c.endermiteLevel);
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
        int y = area.y() + CONTROLS_HEIGHT + 3;
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
