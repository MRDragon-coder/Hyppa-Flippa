package com.hypaflippa.client.ui;

import net.minecraft.client.gui.Font;

import java.util.List;

/**
 * Column positions for a table whose first column is a left-aligned name and whose other
 * columns are right-aligned numbers. Number columns are as wide as their widest cell (or
 * header); the name column gets whatever space is left.
 */
public final class TableLayout {
    public static final int GAP = 8;

    private final int nameLeft;
    private final int nameWidth;
    private final int[] lefts;  // per number column
    private final int[] rights; // per number column

    private TableLayout(int nameLeft, int nameWidth, int[] lefts, int[] rights) {
        this.nameLeft = nameLeft;
        this.nameWidth = nameWidth;
        this.lefts = lefts;
        this.rights = rights;
    }

    /**
     * @param headers the header text of each number column, with any extra space it needs (such
     *                as a sort arrow) already included, so columns don't move when that changes
     * @param rows    each row's cells for the number columns, in the same order as headers
     */
    public static TableLayout of(Font font, Ui.Rect area, String[] headers, List<String[]> rows) {
        int count = headers.length;
        int[] widths = new int[count];
        for (int i = 0; i < count; i++) widths[i] = font.width(headers[i]);
        for (String[] row : rows) {
            for (int i = 0; i < count; i++) widths[i] = Math.max(widths[i], font.width(row[i]));
        }

        int[] lefts = new int[count];
        int[] rights = new int[count];
        int right = area.right() - 4;
        for (int i = count - 1; i >= 0; i--) {
            rights[i] = right;
            lefts[i] = right - widths[i];
            right = lefts[i] - GAP;
        }
        int nameLeft = area.x() + 4;
        return new TableLayout(nameLeft, right - nameLeft, lefts, rights);
    }

    public int nameLeft() { return nameLeft; }
    public int nameWidth() { return nameWidth; }
    public int right(int column) { return rights[column]; }

    /** The column under x: -1 for the name column, otherwise the number column index. Cells split the gaps. */
    public int columnAt(double x) {
        int half = GAP / 2;
        for (int i = 0; i < lefts.length; i++) {
            int end = i + 1 < lefts.length ? lefts[i + 1] - half : Integer.MAX_VALUE;
            if (x >= lefts[i] - half && x < end) return i;
        }
        return -1;
    }
}
