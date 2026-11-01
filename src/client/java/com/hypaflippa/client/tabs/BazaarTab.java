package com.hypaflippa.client.tabs;

import com.hypaflippa.BazaarFlip;
import com.hypaflippa.BazaarFlipperConfig;
import com.hypaflippa.FlipFilters;
import com.hypaflippa.Format;
import com.hypaflippa.NpcFlip;
import com.hypaflippa.client.FlipperState;
import com.hypaflippa.client.ui.Stepper;
import com.hypaflippa.client.ui.Tab;
import com.hypaflippa.client.ui.TableLayout;
import com.hypaflippa.client.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/**
 * Bazaar flips, on two pages that share the same controls:
 * <ul>
 *   <li>Orders: buy with a buy order, sell with a sell offer, keep the gap.</li>
 *   <li>NPC: buy with a buy order or insta-buy, sell to an NPC.</li>
 * </ul>
 * Each page has its own filters and sort order.
 */
public class BazaarTab implements Tab {
    private static final int CONTROL_ROW_HEIGHT = 20;
    private static final int CONTROLS_HEIGHT = 2 * CONTROL_ROW_HEIGHT + 2;
    private static final int HEADER_HEIGHT = 14;
    private static final int ROW_HEIGHT = 18;
    private static final int[] VOLUME_STEPS = {0, 100, 500, 1_000, 5_000, 10_000, 50_000, 100_000,
            500_000, 1_000_000, 5_000_000, 10_000_000};
    private static final String ARROW_DOWN = "▼", ARROW_UP = "▲";

    /** One number column: its sort mode, header text, cell text and cell colour. */
    private record Column<R>(String mode, String label, Function<R, String> cell, ToIntFunction<R> color) {}

    /** What a page lists; the name column (sort mode "name") is always first. */
    private record Page<R>(String id, String buttonLabel, Supplier<FlipFilters> filters, Supplier<List<R>> rows,
                           Function<R, String> productId, List<Column<R>> columns, Supplier<String> waitingMessage) {}

    private static final Page<BazaarFlip> ORDERS = new Page<>(
            BazaarFlipperConfig.PAGE_ORDERS, "Orders", () -> config().orders, FlipperState::flips, BazaarFlip::getProductId,
            List.of(
                    new Column<>("buy", "Buy", flip -> Format.exactCoins(flip.getBuyPrice()), flip -> Ui.TEXT),
                    new Column<>("sell", "Sell", flip -> Format.exactCoins(flip.getSellPrice()), flip -> Ui.TEXT),
                    new Column<>("margin", "Margin", flip -> percent(flip.getProfitPercent()), flip -> Ui.POSITIVE)),
            BazaarTab::bazaarWaitingMessage);

    private static final Page<NpcFlip> NPC = new Page<>(
            BazaarFlipperConfig.PAGE_NPC, "NPC", () -> config().npc, FlipperState::npcFlips, NpcFlip::productId,
            List.of(
                    new Column<>("order", "Order", flip -> Format.exactCoins(flip.buyOrderPrice()), flip -> Ui.TEXT),
                    new Column<>("insta", "Insta", flip -> flip.hasInstaBuy() ? Format.exactCoins(flip.instaBuyPrice()) : "-", flip -> Ui.TEXT),
                    new Column<>("npc", "NPC", flip -> Format.exactCoins(flip.npcPrice()), flip -> Ui.TEXT),
                    new Column<>("orderMargin", "Order %", flip -> percent(flip.orderMarginPercent()), flip -> Ui.POSITIVE),
                    new Column<>("instaMargin", "Insta %", flip -> flip.hasInstaBuy() ? percent(flip.instaMarginPercent()) : "-",
                            flip -> flip.hasInstaBuy() ? Ui.signColor(flip.instaMarginPercent()) : Ui.TEXT_FAINT)),
            () -> FlipperState.data() == null ? bazaarWaitingMessage() : "Loading NPC prices...");

    private static final List<Page<?>> PAGES = List.of(ORDERS, NPC);

    /** @param visible whether the stepper belongs to the open page */
    private record PlacedStepper(int row, int offsetX, Stepper stepper, Supplier<Boolean> visible) {
        PlacedStepper(int row, int offsetX, Stepper stepper) {
            this(row, offsetX, stepper, () -> true);
        }
    }

    // The same settings as the /flips arguments, applied to whichever page is open
    // (sorting is done by clicking column headers), plus the NPC page's insta-margin filter.
    private final List<PlacedStepper> steppers = List.of(
            new PlacedStepper(0, 4, new Stepper(48, () -> String.format(Locale.ROOT, "%s %.1f%%",
                    config().isNpcPage() ? "Ord" : "Min", filters().minProfitPercent), (dir, shift) -> {
                FlipFilters f = filters();
                f.minProfitPercent = Math.max(0, Math.round((f.minProfitPercent + dir * (shift ? 5 : 0.5)) * 10) / 10.0);
            })),
            new PlacedStepper(0, 184, new Stepper(48, () -> filters().minInstaProfitPercent == null ? "Ins off"
                    : String.format(Locale.ROOT, "Ins %.1f%%", filters().minInstaProfitPercent), (dir, shift) -> {
                FlipFilters f = filters();
                // Off below 0%: insta margins are often negative, and "off" keeps buy-order-only flips.
                if (f.minInstaProfitPercent == null) {
                    if (dir > 0) f.minInstaProfitPercent = 0.0;
                } else {
                    double next = Math.round((f.minInstaProfitPercent + dir * (shift ? 5 : 0.5)) * 10) / 10.0;
                    f.minInstaProfitPercent = next < 0 ? null : next;
                }
            }), () -> config().isNpcPage()),
            new PlacedStepper(1, 4, new Stepper(48, () -> "Show " + filters().maxResults, (dir, shift) -> {
                FlipFilters f = filters();
                int step = shift ? 25 : 5;
                // Snap to multiples of the step: 1 -> 5 -> 10 ..., and never below 1.
                f.maxResults = dir > 0 ? (f.maxResults / step + 1) * step : Math.max(1, ((f.maxResults - 1) / step) * step);
            })),
            new PlacedStepper(1, 80, new Stepper(48, () -> "Sell≥" + compact(filters().minSellMovingWeek), (dir, shift) ->
                    filters().minSellMovingWeek = stepVolume(filters().minSellMovingWeek, dir, shift))),
            new PlacedStepper(1, 156, new Stepper(48, () -> "Buy≥" + compact(filters().minBuyMovingWeek), (dir, shift) ->
                    filters().minBuyMovingWeek = stepVolume(filters().minBuyMovingWeek, dir, shift)))
    );

    private double scrollOffset;

    private static BazaarFlipperConfig config() {
        return FlipperState.config;
    }

    private static Page<?> page() {
        return config().isNpcPage() ? NPC : ORDERS;
    }

    private static FlipFilters filters() {
        return page().filters().get();
    }

    @Override public String id() { return "bazaar"; }
    @Override public String title() { return "Bazaar"; }

    // --- Layout, relative to the content area the window gives us

    private static Ui.Rect pageButton(Ui.Rect area, int index) {
        return new Ui.Rect(area.x() + 82 + index * 50, area.y() + 3, 48, 16);
    }

    private static Ui.Rect headerArea(Ui.Rect area) {
        return new Ui.Rect(area.x(), area.y() + CONTROLS_HEIGHT, area.w(), HEADER_HEIGHT);
    }

    private static Ui.Rect listArea(Ui.Rect area) {
        int top = area.y() + CONTROLS_HEIGHT + HEADER_HEIGHT;
        return new Ui.Rect(area.x(), top, area.w(), area.bottom() - top);
    }

    private static int stepperY(Ui.Rect area, PlacedStepper placed) {
        return area.y() + 3 + placed.row() * CONTROL_ROW_HEIGHT;
    }

    private static <R> TableLayout columns(Font font, Ui.Rect header, Page<R> page) {
        // Headers reserve room for the sort arrow, so columns don't jump when the sort changes.
        String[] headers = page.columns().stream().map(c -> ARROW_DOWN + " " + c.label()).toArray(String[]::new);
        List<String[]> cells = new ArrayList<>();
        List<R> rows = page.rows().get();
        if (rows != null) {
            for (R row : rows) cells.add(page.columns().stream().map(c -> c.cell().apply(row)).toArray(String[]::new));
        }
        return TableLayout.of(font, header, headers, cells);
    }

    /** The sort mode of the header cell under x. */
    private static String columnModeAt(TableLayout cols, Page<?> page, double x) {
        int column = cols.columnAt(x);
        return column < 0 ? "name" : page.columns().get(column).mode();
    }

    // --- Input

    @Override
    public void mouseClicked(Ui.Rect area, double mx, double my, boolean shift) {
        for (int i = 0; i < PAGES.size(); i++) {
            if (pageButton(area, i).contains(mx, my)) {
                config().page = PAGES.get(i).id();
                settingsChanged();
                return;
            }
        }
        for (PlacedStepper placed : steppers) {
            if (placed.visible().get() && placed.stepper().click(area.x() + placed.offsetX(), stepperY(area, placed), mx, my, shift)) {
                settingsChanged();
                return;
            }
        }
        Ui.Rect header = headerArea(area);
        if (header.contains(mx, my)) {
            sortBy(columnModeAt(columns(Minecraft.getInstance().font, header, page()), page(), mx));
        }
    }

    @Override
    public void mouseScrolled(Ui.Rect area, double mx, double my, double deltaY) {
        if (listArea(area).contains(mx, my)) {
            scrollOffset -= deltaY * 12;
            clampScroll(area);
        }
    }

    private void clampScroll(Ui.Rect area) {
        List<?> rows = page().rows().get();
        int contentHeight = (rows != null ? rows.size() : 0) * ROW_HEIGHT + 4;
        scrollOffset = Math.clamp(scrollOffset, 0, Math.max(0, contentHeight - listArea(area).h()));
    }

    /** Clicking the active column flips its direction; clicking another column switches to it. */
    private void sortBy(String mode) {
        FlipFilters f = filters();
        if (f.sortBy.equals(mode)) {
            f.sortAscending = !f.sortAscending;
        } else {
            f.sortBy = mode;
            f.sortAscending = FlipFilters.defaultAscending(mode);
        }
        settingsChanged();
    }

    private void settingsChanged() {
        config().save();
        FlipperState.recompute();
        scrollOffset = 0;
    }

    /** Moves a volume filter along VOLUME_STEPS (shift jumps three steps). */
    private static int stepVolume(int value, int direction, boolean shift) {
        for (int n = shift ? 3 : 1; n > 0; n--) {
            int next = value;
            if (direction > 0) {
                for (int step : VOLUME_STEPS) {
                    if (step > value) { next = step; break; }
                }
            } else {
                next = 0;
                for (int step : VOLUME_STEPS) {
                    if (step < value) next = step;
                }
            }
            value = next;
        }
        return value;
    }

    /** 1500 -> "1.5k", 2000000 -> "2M". */
    private static String compact(int value) {
        if (value >= 1_000_000) return trimDecimal(value / 1_000_000.0) + "M";
        if (value >= 1_000) return trimDecimal(value / 1_000.0) + "k";
        return String.valueOf(value);
    }

    private static String trimDecimal(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }

    private static String percent(double value) {
        return String.format(Locale.ROOT, "%.1f%%", value);
    }

    private static String bazaarWaitingMessage() {
        return FlipperState.error() != null ? FlipperState.error() : "Loading...";
    }

    // --- Rendering

    @Override
    public void render(GuiGraphicsExtractor guis, Font font, Ui.Rect area, int mouseX, int mouseY) {
        renderPage(guis, font, area, mouseX, mouseY, page());
    }

    private <R> void renderPage(GuiGraphicsExtractor guis, Font font, Ui.Rect area, int mouseX, int mouseY, Page<R> page) {
        clampScroll(area);

        // Controls
        guis.fill(area.x() - 2, area.y(), area.right() + 2, area.y() + CONTROLS_HEIGHT, Ui.CONTROLS_BG);
        for (PlacedStepper placed : steppers) {
            if (!placed.visible().get()) continue;
            placed.stepper().render(guis, font, area.x() + placed.offsetX(), stepperY(area, placed), mouseX, mouseY);
        }
        for (int i = 0; i < PAGES.size(); i++) {
            Ui.button(guis, font, pageButton(area, i), PAGES.get(i).buttonLabel(), mouseX, mouseY, PAGES.get(i) == page);
        }

        // Column header (stays put while the list below it scrolls)
        FlipFilters f = page.filters().get();
        List<R> rows = page.rows().get();
        Ui.Rect header = headerArea(area);
        TableLayout cols = columns(font, header, page);
        int headerTextY = header.y() + 3;
        String hovered = header.contains(mouseX, mouseY) ? columnModeAt(cols, page, mouseX) : null;
        Ui.fill(guis, header, Ui.HEADER_BG);
        guis.fill(header.x(), header.bottom() - 1, header.right(), header.bottom(), Ui.DIVIDER);
        guis.text(font, headerLabel(f, "name", "Name"), cols.nameLeft(), headerTextY, headerColor(f, "name", hovered));
        for (int i = 0; i < page.columns().size(); i++) {
            Column<R> column = page.columns().get(i);
            Ui.textRight(guis, font, headerLabel(f, column.mode(), column.label()), cols.right(i), headerTextY,
                    headerColor(f, column.mode(), hovered));
        }

        // Flip list
        Ui.Rect list = listArea(area);
        guis.enableScissor(list.x(), list.y(), list.right(), list.bottom());
        if (rows != null && !rows.isEmpty()) {
            int startIndex = (int) (scrollOffset / ROW_HEIGHT);
            int visibleCount = (int) Math.ceil(list.h() / (double) ROW_HEIGHT) + 1;
            int endIndex = Math.min(rows.size(), startIndex + visibleCount);

            for (int i = startIndex; i < endIndex; i++) {
                R row = rows.get(i);
                int rowY = list.y() + 2 + (i * ROW_HEIGHT) - (int) scrollOffset;
                if (i % 2 == 0) {
                    guis.fill(list.x(), rowY, list.right(), rowY + ROW_HEIGHT - 2, Ui.ROW_STRIPE);
                }
                int textY = rowY + 4;
                guis.text(font, Ui.fit(font, FlipperState.itemName(page.productId().apply(row)), cols.nameWidth()), cols.nameLeft(), textY, Ui.TEXT);
                for (int c = 0; c < page.columns().size(); c++) {
                    Column<R> column = page.columns().get(c);
                    Ui.textRight(guis, font, column.cell().apply(row), cols.right(c), textY, column.color().applyAsInt(row));
                }
            }
        } else {
            String message = rows != null ? "No profitable flips found." : page.waitingMessage().get();
            guis.centeredText(font, message, list.x() + list.w() / 2, list.y() + list.h() / 2 - 4, Ui.TEXT_FAINT);
        }
        guis.disableScissor();
    }

    /** Adds the sort arrow to the active column: after "Name", before right-aligned number headers. */
    private static String headerLabel(FlipFilters f, String mode, String label) {
        if (!f.sortBy.equals(mode)) return label;
        String arrow = f.sortAscending ? ARROW_UP : ARROW_DOWN;
        return mode.equals("name") ? label + " " + arrow : arrow + " " + label;
    }

    private static int headerColor(FlipFilters f, String mode, String hovered) {
        if (mode.equals(hovered)) return Ui.TEXT;
        return f.sortBy.equals(mode) ? Ui.ACCENT : Ui.TEXT_DIM;
    }
}
