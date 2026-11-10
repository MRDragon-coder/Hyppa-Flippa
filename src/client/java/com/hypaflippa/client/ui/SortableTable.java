package com.hypaflippa.client.ui;

import com.hypaflippa.SortSettings;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/**
 * A list with a name column and number columns: click a header to sort, the ≡ button picks which
 * columns show, hover a header for what it means. It sits below a tab's {@link Controls}; the ≡
 * button goes at the right end of the controls' second row.
 *
 * The cell texts are worked out once per new list of rows rather than every frame: lists are
 * replaced on every recompute, which is also when anything shown in a cell can change.
 *
 * @param <R> one row
 */
public final class SortableTable<R> {
    private static final int HEADER_HEIGHT = 14;
    private static final int ROW_HEIGHT = 18;
    private static final int PICKER_ROW_HEIGHT = 14;
    private static final String ARROW_DOWN = "▼", ARROW_UP = "▲";
    private static final Ui.Help COLUMNS_HELP = new Ui.Help("Columns", "Choose which columns to show.");

    /** One number column: its sort mode, header text, cell text, cell colour and hover help. */
    public record Column<R>(String mode, String label, Function<R, String> cell, ToIntFunction<R> color, Ui.Help help) {}

    private final int top;
    private final Supplier<? extends SortSettings> settings;
    private final Supplier<List<R>> rows;
    private final Function<R, String> name;
    private final Ui.Help nameHelp;
    private final List<Column<R>> columns;
    private final String emptyMessage;
    private final Supplier<String> waitingMessage;
    private final Runnable onChange;

    private double scrollOffset;
    private boolean pickerOpen;

    // What the last frame drew, reused while the rows, columns and width stay the same.
    private List<R> cachedRows;
    private List<Column<R>> cachedColumns;
    private int cachedLeft = Integer.MIN_VALUE, cachedRight;
    private TableLayout cachedLayout;
    private String[][] cachedCells;
    private int[][] cachedColors;

    /**
     * @param top            height of the controls above the header
     * @param settings       sort order and hidden columns
     * @param rows           the rows to show, null while they're still loading
     * @param name           the name column's text of a row
     * @param emptyMessage   shown when there are no rows
     * @param waitingMessage shown while the rows are null
     * @param onChange       runs after the sort or the visible columns changed (save, recompute)
     */
    public SortableTable(int top, Supplier<? extends SortSettings> settings, Supplier<List<R>> rows,
                         Function<R, String> name, Ui.Help nameHelp, List<Column<R>> columns,
                         String emptyMessage, Supplier<String> waitingMessage, Runnable onChange) {
        this.top = top;
        this.settings = settings;
        this.rows = rows;
        this.name = name;
        this.nameHelp = nameHelp;
        this.columns = columns;
        this.emptyMessage = emptyMessage;
        this.waitingMessage = waitingMessage;
        this.onChange = onChange;
    }

    public boolean isPickerOpen() {
        return pickerOpen;
    }

    public void resetScroll() {
        scrollOffset = 0;
    }

    // --- Layout

    private Ui.Rect headerArea(Ui.Rect area) {
        return new Ui.Rect(area.x(), area.y() + top, area.w(), HEADER_HEIGHT);
    }

    private Ui.Rect listArea(Ui.Rect area) {
        int listTop = area.y() + top + HEADER_HEIGHT;
        return new Ui.Rect(area.x(), listTop, area.w(), area.bottom() - listTop);
    }

    private static Ui.Rect columnsButton(Ui.Rect area) {
        return new Ui.Rect(area.right() - 24, area.y() + 3 + Controls.ROW_HEIGHT, 22, 16);
    }

    /** Long column lists get two columns of checkboxes, so the picker still fits in a small window. */
    private int pickerColumns() {
        return columns.size() > 8 ? 2 : 1;
    }

    private int pickerRows() {
        return (columns.size() + pickerColumns() - 1) / pickerColumns();
    }

    private int pickerColumnWidth() {
        return pickerColumns() == 1 ? 96 : 80;
    }

    /** The column picker, opening down from the ≡ button. */
    private Ui.Rect pickerArea(Ui.Rect area) {
        Ui.Rect button = columnsButton(area);
        int width = pickerColumns() * pickerColumnWidth() + 4;
        return new Ui.Rect(button.right() - width, button.bottom() + 1, width, pickerRows() * PICKER_ROW_HEIGHT + 4);
    }

    private Ui.Rect pickerRow(Ui.Rect picker, int index) {
        return new Ui.Rect(picker.x() + 2 + (index / pickerRows()) * pickerColumnWidth(),
                picker.y() + 2 + (index % pickerRows()) * PICKER_ROW_HEIGHT, pickerColumnWidth(), PICKER_ROW_HEIGHT);
    }

    private List<Column<R>> visibleColumns() {
        SortSettings s = settings.get();
        return columns.stream().filter(c -> !s.isColumnHidden(c.mode())).toList();
    }

    /** Column positions and cell texts for the current rows, worked out again only when something changed. */
    private TableLayout layout(Font font, Ui.Rect area) {
        List<R> current = rows.get();
        List<Column<R>> visible = visibleColumns();
        Ui.Rect header = headerArea(area);
        if (current == cachedRows && visible.equals(cachedColumns) && header.x() == cachedLeft && header.right() == cachedRight) {
            return cachedLayout;
        }
        int count = current == null ? 0 : current.size();
        cachedCells = new String[count][];
        cachedColors = new int[count][];
        for (int r = 0; r < count; r++) {
            R row = current.get(r);
            cachedCells[r] = new String[visible.size()];
            cachedColors[r] = new int[visible.size()];
            for (int c = 0; c < visible.size(); c++) {
                cachedCells[r][c] = visible.get(c).cell().apply(row);
                cachedColors[r][c] = visible.get(c).color().applyAsInt(row);
            }
        }
        // Headers reserve room for the sort arrow, so columns don't jump when the sort changes.
        String[] headers = visible.stream().map(c -> ARROW_DOWN + " " + c.label()).toArray(String[]::new);
        cachedLayout = TableLayout.of(font, header, headers, List.of(cachedCells));
        cachedRows = current;
        cachedColumns = visible;
        cachedLeft = header.x();
        cachedRight = header.right();
        return cachedLayout;
    }

    /** The sort mode of the header cell under x. */
    private String columnModeAt(TableLayout cols, double x) {
        int column = cols.columnAt(x);
        return column < 0 ? "name" : cachedColumns.get(column).mode();
    }

    /** The row under the mouse, or null. */
    public R rowAt(Ui.Rect area, double mx, double my) {
        Ui.Rect list = listArea(area);
        List<R> current = rows.get();
        if (pickerOpen || current == null || !list.contains(mx, my)) return null;
        int index = (int) ((my - list.y() - 2 + scrollOffset) / ROW_HEIGHT);
        return index >= 0 && index < current.size() ? current.get(index) : null;
    }

    // --- Input

    /**
     * Handles the column picker and header clicks.
     *
     * @return true if the click was used; false lets the tab's controls have it (a click outside
     *         an open picker closes it and still works on whatever it hit)
     */
    public boolean click(Font font, Ui.Rect area, double mx, double my) {
        if (pickerOpen) {
            Ui.Rect picker = pickerArea(area);
            if (picker.contains(mx, my)) {
                for (int i = 0; i < columns.size(); i++) {
                    if (pickerRow(picker, i).contains(mx, my)) {
                        settings.get().toggleColumn(columns.get(i).mode());
                        changed();
                    }
                }
                return true;
            }
            pickerOpen = false;
            if (columnsButton(area).contains(mx, my)) return true; // the ≡ button only closes it
        } else if (columnsButton(area).contains(mx, my)) {
            pickerOpen = true;
            return true;
        }
        if (headerArea(area).contains(mx, my)) {
            settings.get().clickSort(columnModeAt(layout(font, area), mx));
            changed();
            return true;
        }
        return false;
    }

    private void changed() {
        onChange.run();
        scrollOffset = 0;
    }

    public void mouseScrolled(Ui.Rect area, double mx, double my, double deltaY) {
        if (listArea(area).contains(mx, my)) {
            scrollOffset -= deltaY * 12;
            clampScroll(area);
        }
    }

    private void clampScroll(Ui.Rect area) {
        List<R> current = rows.get();
        int contentHeight = (current != null ? current.size() : 0) * ROW_HEIGHT + 4;
        scrollOffset = Math.clamp(scrollOffset, 0, Math.max(0, contentHeight - listArea(area).h()));
    }

    /** The help of the header cell or ≡ button under the mouse, or null. */
    public Ui.Help helpAt(Font font, Ui.Rect area, double mx, double my) {
        if (pickerOpen) return null;
        if (columnsButton(area).contains(mx, my)) return COLUMNS_HELP;
        if (!headerArea(area).contains(mx, my)) return null;
        TableLayout cols = layout(font, area);
        int column = cols.columnAt(mx);
        Ui.Help help = column < 0 ? nameHelp : cachedColumns.get(column).help();
        return help == null ? null : help.plus(Ui.SORT_HINT);
    }

    // --- Rendering

    public void render(GuiGraphicsExtractor guis, Font font, Ui.Rect area, int mouseX, int mouseY) {
        clampScroll(area);
        SortSettings s = settings.get();
        List<R> current = rows.get();
        TableLayout cols = layout(font, area);

        // Column header (stays put while the list below it scrolls)
        Ui.Rect header = headerArea(area);
        int headerTextY = header.y() + 3;
        String hovered = header.contains(mouseX, mouseY) ? columnModeAt(cols, mouseX) : null;
        Ui.fill(guis, header, Ui.HEADER_BG);
        guis.fill(header.x(), header.bottom() - 1, header.right(), header.bottom(), Ui.DIVIDER);
        guis.text(font, headerLabel(s, "name", "Name"), cols.nameLeft(), headerTextY, headerColor(s, "name", hovered));
        for (int i = 0; i < cachedColumns.size(); i++) {
            Column<R> column = cachedColumns.get(i);
            Ui.textRight(guis, font, headerLabel(s, column.mode(), column.label()), cols.right(i), headerTextY,
                    headerColor(s, column.mode(), hovered));
        }

        // Rows
        Ui.Rect list = listArea(area);
        guis.enableScissor(list.x(), list.y(), list.right(), list.bottom());
        if (current != null && !current.isEmpty()) {
            int startIndex = (int) (scrollOffset / ROW_HEIGHT);
            int visibleCount = (int) Math.ceil(list.h() / (double) ROW_HEIGHT) + 1;
            int endIndex = Math.min(current.size(), startIndex + visibleCount);
            for (int i = startIndex; i < endIndex; i++) {
                int rowY = list.y() + 2 + (i * ROW_HEIGHT) - (int) scrollOffset;
                if (i % 2 == 0) guis.fill(list.x(), rowY, list.right(), rowY + ROW_HEIGHT - 2, Ui.ROW_STRIPE);
                int textY = rowY + 4;
                guis.text(font, Ui.fit(font, name.apply(current.get(i)), cols.nameWidth()), cols.nameLeft(), textY, Ui.TEXT);
                for (int c = 0; c < cachedColumns.size(); c++) {
                    Ui.textRight(guis, font, cachedCells[i][c], cols.right(c), textY, cachedColors[i][c]);
                }
            }
        } else {
            String message = current != null ? emptyMessage : waitingMessage.get();
            guis.centeredText(font, message, list.x() + list.w() / 2, list.y() + list.h() / 2 - 4, Ui.TEXT_FAINT);
        }
        guis.disableScissor();

        // Column picker: the ≡ button, and when open every column with a checkbox.
        Ui.button(guis, font, columnsButton(area), "≡", mouseX, mouseY, pickerOpen);
        if (pickerOpen) {
            guis.nextStratum(); // above the list, so its text can't show through
            Ui.Rect picker = pickerArea(area);
            Ui.fill(guis, picker, 0xF0181818);
            guis.outline(picker.x(), picker.y(), picker.w(), picker.h(), Ui.DIVIDER);
            for (int i = 0; i < columns.size(); i++) {
                Column<R> column = columns.get(i);
                Ui.Rect row = pickerRow(picker, i);
                if (row.contains(mouseX, mouseY)) Ui.fill(guis, row, Ui.ROW_STRIPE);
                boolean shown = !s.isColumnHidden(column.mode());
                guis.text(font, (shown ? "☑ " : "☐ ") + column.label(), row.x() + 4, row.y() + 3,
                        shown ? Ui.TEXT : Ui.TEXT_FAINT);
            }
        }
    }

    /** Adds the sort arrow to the active column: after "Name", before right-aligned number headers. */
    private static String headerLabel(SortSettings s, String mode, String label) {
        if (!mode.equals(s.sortBy)) return label;
        String arrow = s.sortAscending ? ARROW_UP : ARROW_DOWN;
        return mode.equals("name") ? label + " " + arrow : arrow + " " + label;
    }

    private static int headerColor(SortSettings s, String mode, String hovered) {
        if (mode.equals(hovered)) return Ui.TEXT;
        return mode.equals(s.sortBy) ? Ui.ACCENT : Ui.TEXT_DIM;
    }
}
