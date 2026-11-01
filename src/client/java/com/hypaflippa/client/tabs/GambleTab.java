package com.hypaflippa.client.tabs;

import com.hypaflippa.BazaarData;
import com.hypaflippa.BazaarPrices;
import com.hypaflippa.Format;
import com.hypaflippa.HypaFlippaConfig;
import com.hypaflippa.client.FlipperState;
import com.hypaflippa.client.ui.Tab;
import com.hypaflippa.client.ui.TableLayout;
import com.hypaflippa.client.ui.Ui;
import com.hypaflippa.gamble.GambleCalculator;
import com.hypaflippa.gamble.GambleData;
import com.hypaflippa.gamble.GambleResult;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * A gamble you buy and open, with one weighted roll per opening (Jerry Boxes, gifts): a table of
 * every option on top, and the breakdown of the selected one below.
 */
public class GambleTab implements Tab {
    private static final int CONTROLS_HEIGHT = 22;
    private static final int HEADER_HEIGHT = 14;
    private static final int ROW_HEIGHT = 14;
    private static final int TITLE_HEIGHT = 14;
    private static final int FOOTNOTE_HEIGHT = 12;
    private static final String[] HEADERS = {"Cost", "Return", "Profit", "ROI"};

    private final String id;
    private final String title;
    private final Supplier<List<GambleData.Table>> tables;
    /** Gifts give the other player a roll too; only then is the "Rolls" toggle shown. */
    private final boolean twoPlayer;

    private final BreakdownView breakdown = new BreakdownView();
    private int selected;

    public GambleTab(String id, String title, Supplier<List<GambleData.Table>> tables, boolean twoPlayer) {
        this.id = id;
        this.title = title;
        this.tables = tables;
        this.twoPlayer = twoPlayer;
    }

    private static HypaFlippaConfig config() {
        return FlipperState.general;
    }

    @Override public String id() { return id; }
    @Override public String title() { return title; }

    private int rolls() {
        return twoPlayer && config().giftBothRolls ? 2 : 1;
    }

    private List<GambleResult> results(BazaarData data) {
        List<GambleResult> results = new ArrayList<>();
        for (GambleData.Table table : tables.get()) {
            results.add(GambleCalculator.evaluate(table, data, FlipperState.auctions(), config().priceMode, rolls()));
        }
        return results;
    }

    // --- Layout

    private static Ui.Rect priceModeButton(Ui.Rect area) { return new Ui.Rect(area.x() + 4, area.y() + 3, 92, 16); }
    private static Ui.Rect rollsButton(Ui.Rect area) { return new Ui.Rect(area.x() + 100, area.y() + 3, 92, 16); }
    private static Ui.Rect header(Ui.Rect area) { return new Ui.Rect(area.x(), area.y() + CONTROLS_HEIGHT, area.w(), HEADER_HEIGHT); }

    private Ui.Rect row(Ui.Rect area, int index) {
        return new Ui.Rect(area.x(), area.y() + CONTROLS_HEIGHT + HEADER_HEIGHT + index * ROW_HEIGHT, area.w(), ROW_HEIGHT);
    }

    private Ui.Rect breakdownTitle(Ui.Rect area) {
        Ui.Rect lastRow = row(area, tables.get().size());
        return new Ui.Rect(area.x(), lastRow.y() + 2, area.w(), TITLE_HEIGHT);
    }

    private Ui.Rect breakdownArea(Ui.Rect area) {
        int top = breakdownTitle(area).bottom();
        // The bottom line is kept free for the "no price" footnote.
        return new Ui.Rect(area.x(), top, area.w(), area.bottom() - FOOTNOTE_HEIGHT - top);
    }

    // --- Input

    @Override
    public void mouseClicked(Ui.Rect area, double mx, double my, boolean shift) {
        HypaFlippaConfig c = config();
        if (priceModeButton(area).contains(mx, my)) {
            c.priceMode = c.priceMode == BazaarPrices.Mode.ORDER ? BazaarPrices.Mode.INSTANT : BazaarPrices.Mode.ORDER;
            c.save();
        } else if (twoPlayer && rollsButton(area).contains(mx, my)) {
            c.giftBothRolls = !c.giftBothRolls;
            c.save();
        } else {
            for (int i = 0; i < tables.get().size(); i++) {
                if (row(area, i).contains(mx, my)) select(i);
            }
        }
    }

    private void select(int index) {
        if (index != selected) breakdown.resetScroll();
        selected = index;
    }

    @Override
    public void mouseScrolled(Ui.Rect area, double mx, double my, double deltaY) {
        if (breakdownArea(area).contains(mx, my)) breakdown.mouseScrolled(deltaY);
    }

    @Override
    public boolean focusBazaarPage(String title) {
        List<GambleData.Table> list = tables.get();
        for (int i = 0; i < list.size(); i++) {
            if (Ui.titleShowsItem(title, list.get(i).name)) {
                select(i);
                return true;
            }
        }
        return false;
    }

    // --- Rendering

    @Override
    public void render(GuiGraphicsExtractor guis, Font font, Ui.Rect area, int mouseX, int mouseY) {
        HypaFlippaConfig c = config();
        guis.fill(area.x() - 2, area.y(), area.right() + 2, area.y() + CONTROLS_HEIGHT, Ui.CONTROLS_BG);
        Ui.button(guis, font, priceModeButton(area), c.priceMode == BazaarPrices.Mode.ORDER ? "Prices: Order" : "Prices: Instant", mouseX, mouseY);
        if (twoPlayer) {
            Ui.button(guis, font, rollsButton(area), c.giftBothRolls ? "Rolls: Both" : "Rolls: Yours", mouseX, mouseY);
        }

        BazaarData data = FlipperState.data();
        if (data == null) {
            String message = FlipperState.error() != null ? FlipperState.error() : "Loading...";
            guis.centeredText(font, message, area.x() + area.w() / 2, area.y() + area.h() / 2, Ui.TEXT_FAINT);
            return;
        }

        List<GambleResult> results = results(data);
        selected = Math.clamp(selected, 0, results.size() - 1);

        List<String[]> cells = new ArrayList<>();
        for (GambleResult result : results) cells.add(cells(result));
        Ui.Rect header = header(area);
        TableLayout cols = TableLayout.of(font, header, HEADERS, cells);

        Ui.fill(guis, header, Ui.HEADER_BG);
        guis.fill(header.x(), header.bottom() - 1, header.right(), header.bottom(), Ui.DIVIDER);
        guis.text(font, "Name", cols.nameLeft(), header.y() + 3, Ui.TEXT_DIM);
        for (int i = 0; i < HEADERS.length; i++) {
            Ui.textRight(guis, font, HEADERS[i], cols.right(i), header.y() + 3, Ui.TEXT_DIM);
        }

        for (int i = 0; i < results.size(); i++) {
            GambleResult result = results.get(i);
            Ui.Rect row = row(area, i);
            if (i == selected) Ui.fill(guis, row, Ui.ROW_SELECTED);
            else if (row.contains(mouseX, mouseY)) Ui.fill(guis, row, Ui.ROW_STRIPE);
            int textY = row.y() + 3;
            String name = result.name() + (result.isIncomplete() ? "*" : "");
            guis.text(font, Ui.fit(font, name, cols.nameWidth()), cols.nameLeft(), textY, Ui.TEXT);
            String[] rowCells = cells.get(i);
            for (int col = 0; col < rowCells.length; col++) {
                int color = col >= 2 && result.hasCost() ? Ui.signColor(result.profit()) : Ui.TEXT;
                Ui.textRight(guis, font, rowCells[col], cols.right(col), textY, color);
            }
        }

        GambleResult chosen = results.get(selected);
        Ui.Rect titleRow = breakdownTitle(area);
        guis.fill(titleRow.x(), titleRow.y(), titleRow.right(), titleRow.y() + 1, Ui.DIVIDER);
        String subtitle = chosen.name() + (rolls() > 1 ? " (both players)" : "") + ", per opening";
        guis.text(font, Ui.fit(font, subtitle, titleRow.w() - 8), titleRow.x() + 4, titleRow.y() + 4, Ui.ACCENT);
        breakdown.render(guis, font, breakdownArea(area), chosen);

        guis.text(font, Ui.fit(font, footnote(results), area.w() - 8), area.x() + 4, area.bottom() - 10, Ui.TEXT_FAINT);
    }

    /** Auction House status, plus a warning when some drops couldn't be priced. */
    private static String footnote(List<GambleResult> results) {
        String auctionStatus;
        if (FlipperState.auctions() == null) {
            auctionStatus = FlipperState.auctionFetchFailed() ? "AH prices: fetch failed" : "AH prices: loading...";
        } else {
            long minutes = (System.currentTimeMillis() - FlipperState.auctions().fetchedAtMs()) / 60_000;
            auctionStatus = "AH prices: " + (minutes == 0 ? "just now" : minutes + "m ago")
                    + (FlipperState.isFetchingAuctions() ? " (updating)" : "");
        }
        boolean incomplete = results.stream().anyMatch(GambleResult::isIncomplete);
        return incomplete ? "* no price for some drops | " + auctionStatus : auctionStatus;
    }

    private static String[] cells(GambleResult result) {
        if (!result.hasCost()) {
            return new String[] {"?", Format.coins(result.expectedReturn()), "?", "?"};
        }
        return new String[] {Format.coins(result.cost().getAsDouble()), Format.coins(result.expectedReturn()),
                Format.signedCoins(result.profit()), Format.percent(result.roi())};
    }
}
