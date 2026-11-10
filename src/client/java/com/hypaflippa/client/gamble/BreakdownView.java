package com.hypaflippa.client.gamble;

import com.hypaflippa.Format;
import com.hypaflippa.client.ui.TableLayout;
import com.hypaflippa.client.ui.Ui;
import com.hypaflippa.gamble.GambleResult;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * Where a gamble's average profit comes from, one line per source:
 * <pre>
 *   Cost                1 × 61,234.5     -61.2k
 *   20,000 Coins          44.1%          +8.81k
 *   Jerry Stone       0.88% × 1.2M       +10.6k
 *   Jerry-chine Gun       1.32%        no price
 *   Avg profit            +12.3%         +7.5k
 * </pre>
 * Scrolls on its own when the lines don't fit.
 */
final class BreakdownView {
    private static final int LINE_HEIGHT = 12;

    private double scrollOffset;

    void resetScroll() {
        scrollOffset = 0;
    }

    private record Row(String label, String detail, String value, int labelColor, int valueColor) {}

    private static List<Row> rows(GambleResult result) {
        List<Row> rows = new ArrayList<>();
        if (result.hasCost()) {
            rows.add(new Row("Cost", result.costDetail(), Format.signedCoins(-result.cost().getAsDouble()), Ui.TEXT, Ui.NEGATIVE));
        } else {
            rows.add(new Row("Cost", result.costDetail(), "?", Ui.TEXT, Ui.TEXT_FAINT));
        }
        for (GambleResult.Line line : result.lines()) {
            rows.add(switch (line.kind()) {
                case VALUE -> new Row(line.label(), line.detail(), Format.signedCoins(line.value()), Ui.TEXT, Ui.POSITIVE);
                case NO_PRICE -> new Row(line.label(), line.detail(), "no price", Ui.TEXT_DIM, Ui.TEXT_FAINT);
                case NOT_COUNTED -> new Row(line.label(), line.detail(), "not counted", Ui.TEXT_DIM, Ui.TEXT_FAINT);
                case INFO -> new Row(line.label(), line.detail(), "", Ui.TEXT_DIM, Ui.TEXT_FAINT);
            });
        }
        if (result.hasCost()) {
            rows.add(new Row("Avg profit", Format.percent(result.roi()), Format.signedCoins(result.profit()), Ui.ACCENT, Ui.signColor(result.profit())));
        }
        return rows;
    }

    void render(GuiGraphicsExtractor guis, Font font, Ui.Rect area, GambleResult result) {
        List<Row> rows = rows(result);
        List<String[]> cells = new ArrayList<>();
        for (Row row : rows) cells.add(new String[] {row.detail(), row.value()});
        TableLayout cols = TableLayout.of(font, area, new String[] {"", ""}, cells);

        int contentHeight = rows.size() * LINE_HEIGHT + 2;
        scrollOffset = Math.clamp(scrollOffset, 0, Math.max(0, contentHeight - area.h()));

        guis.enableScissor(area.x(), area.y(), area.right(), area.bottom());
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int y = area.y() + 2 + i * LINE_HEIGHT - (int) scrollOffset;
            if (y + LINE_HEIGHT < area.y() || y > area.bottom()) continue;
            boolean total = i == rows.size() - 1 && result.hasCost();
            if (total) guis.fill(area.x(), y - 2, area.right(), y - 1, Ui.DIVIDER);
            guis.text(font, Ui.fit(font, row.label(), cols.nameWidth()), cols.nameLeft(), y, row.labelColor());
            Ui.textRight(guis, font, row.detail(), cols.right(0), y, Ui.TEXT_DIM);
            Ui.textRight(guis, font, row.value(), cols.right(1), y, row.valueColor());
        }
        guis.disableScissor();
    }

    void mouseScrolled(double deltaY) {
        scrollOffset -= deltaY * 12;
    }
}
