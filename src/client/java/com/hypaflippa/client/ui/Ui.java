package com.hypaflippa.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Small drawing helpers and the colour palette shared by the window and all tabs. */
public final class Ui {
    public static final int TEXT = 0xFFFFFFFF;
    public static final int TEXT_DIM = 0xFFAAAAAA;
    public static final int TEXT_FAINT = 0xFF888888;
    public static final int ACCENT = 0xFFFFFF55;
    public static final int POSITIVE = 0xFF55FF55;
    public static final int NEGATIVE = 0xFFFF5555;

    public static final int BUTTON = 0xFF555555;
    public static final int BUTTON_HOVER = 0xFF888888;
    public static final int BUTTON_ACTIVE = 0xFF3A6EA5;
    public static final int HEADER_BG = 0xFF2A2A2A;
    public static final int CONTROLS_BG = 0xFF222222;
    public static final int ROW_STRIPE = 0x33FFFFFF;
    public static final int ROW_SELECTED = 0x553A6EA5;
    public static final int DIVIDER = 0xFF555555;

    private Ui() {}

    public record Rect(int x, int y, int w, int h) {
        public boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }

        public int right() { return x + w; }
        public int bottom() { return y + h; }
    }

    public static void fill(GuiGraphicsExtractor guis, Rect r, int color) {
        guis.fill(r.x(), r.y(), r.right(), r.bottom(), color);
    }

    public static void button(GuiGraphicsExtractor guis, Font font, Rect r, String label, int mouseX, int mouseY) {
        button(guis, font, r, label, mouseX, mouseY, false);
    }

    /** @param active draws the button highlighted (selected tab, toggled option) */
    public static void button(GuiGraphicsExtractor guis, Font font, Rect r, String label, int mouseX, int mouseY, boolean active) {
        int color = r.contains(mouseX, mouseY) ? BUTTON_HOVER : active ? BUTTON_ACTIVE : BUTTON;
        fill(guis, r, color);
        guis.centeredText(font, fit(font, label, r.w() - 2), r.x() + r.w() / 2, r.y() + (r.h() - 8) / 2, TEXT);
    }

    public static void textRight(GuiGraphicsExtractor guis, Font font, String text, int right, int y, int color) {
        guis.text(font, text, right - font.width(text), y, color);
    }

    /** Cuts text that doesn't fit and adds "..." so it never runs into the next column. */
    public static String fit(Font font, String text, int width) {
        if (font.width(text) <= width) return text;
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width("..."))) + "...";
    }

    // --- Tooltips

    /** What a control or column is for, shown when you hover it. */
    public record Help(String title, String text) {
        /** The same help with another line added, e.g. how to use the control. */
        public Help plus(String line) {
            return new Help(title, text + "\n" + line);
        }
    }

    /** Added to the help of steppers and sortable column headers. */
    public static final String STEP_HINT = "Shift-click for bigger steps.";
    public static final String SORT_HINT = "Click to sort by this, click again to reverse.";

    /** Hover this long before a help tooltip shows, so they don't flash while you move the mouse past. */
    private static final long TOOLTIP_DELAY_MS = 400;
    private static final int TOOLTIP_MAX_WIDTH = 170;
    private static final int TOOLTIP_LINE_HEIGHT = 10;
    private static String tooltipKey;
    private static long tooltipSinceMs, tooltipSeenMs;

    /**
     * Draws the background of a hover box of this size next to the mouse, flipped to the other
     * side where it would leave {@code bounds}, on top of everything else drawn so far.
     *
     * @return the top-left corner of the box's inside (4 pixels in from its edge)
     */
    public static int[] hoverBox(GuiGraphicsExtractor guis, Rect bounds, int mouseX, int mouseY, int innerWidth, int innerHeight) {
        int boxW = innerWidth + 8, boxH = innerHeight + 8;
        int x = mouseX + 10, y = mouseY + 10;
        if (x + boxW > bounds.right()) x = Math.max(bounds.x(), mouseX - 10 - boxW);
        if (y + boxH > bounds.bottom()) y = Math.max(bounds.y(), mouseY - 10 - boxH);
        guis.nextStratum();
        guis.fill(x, y, x + boxW, y + boxH, 0xF0181818);
        guis.outline(x, y, boxW, boxH, DIVIDER);
        return new int[] {x + 4, y + 4};
    }

    /**
     * A small help box: a title, then the text wrapped to a narrow width ("\n" starts a new line).
     * Call it every frame while the mouse is over the thing it explains; it appears after a short hover.
     */
    public static void helpTooltip(GuiGraphicsExtractor guis, Font font, Rect bounds, int mouseX, int mouseY, Help help) {
        String title = help.title(), text = help.text();
        long now = System.currentTimeMillis();
        // A new target, or the same one hovered again after the mouse left it, restarts the delay.
        if (!title.equals(tooltipKey) || now - tooltipSeenMs > 150) {
            tooltipKey = title;
            tooltipSinceMs = now;
        }
        tooltipSeenMs = now;
        if (now - tooltipSinceMs < TOOLTIP_DELAY_MS) return;

        List<String> lines = wrap(font, text, Math.max(TOOLTIP_MAX_WIDTH, font.width(title)));
        int width = font.width(title);
        for (String line : lines) width = Math.max(width, font.width(line));
        int[] at = hoverBox(guis, bounds, mouseX, mouseY, width, (lines.size() + 1) * TOOLTIP_LINE_HEIGHT - 2);
        guis.text(font, title, at[0], at[1], ACCENT);
        for (int i = 0; i < lines.size(); i++) {
            guis.text(font, lines.get(i), at[0], at[1] + (i + 1) * TOOLTIP_LINE_HEIGHT, TEXT_DIM);
        }
    }

    /** Splits text into lines no wider than {@code width}, at spaces; "\n" starts a new line. */
    private static List<String> wrap(Font font, String text, int width) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\n")) {
            String line = "";
            for (String word : paragraph.split(" ")) {
                String next = line.isEmpty() ? word : line + " " + word;
                if (!line.isEmpty() && font.width(next) > width) {
                    lines.add(line);
                    line = word;
                } else {
                    line = next;
                }
            }
            lines.add(line);
        }
        return lines;
    }

    public static int signColor(double value) {
        return value > 0 ? POSITIVE : value < 0 ? NEGATIVE : TEXT;
    }

    /** ENCHANTED_RAW_BEEF -> "Enchanted Raw Beef". */
    public static String displayName(String productId) {
        StringBuilder name = new StringBuilder();
        for (String word : productId.toLowerCase(Locale.ROOT).split("_")) {
            if (word.isEmpty()) continue;
            if (!name.isEmpty()) name.append(' ');
            name.append(Character.toUpperCase(word.charAt(0))).append(word, 1, word.length());
        }
        return name.toString();
    }

    /**
     * Whether a chest title is the bazaar page of an item called {@code name}. Hypixel titles look
     * like "Category ➜ Item Name" and long ones get cut off, so a cut-off item name also matches.
     */
    public static boolean titleShowsItem(String title, String name) {
        String lowerTitle = title.toLowerCase(Locale.ROOT);
        String lowerName = name.toLowerCase(Locale.ROOT);
        if (lowerTitle.contains(lowerName)) return true;
        int arrow = lowerTitle.lastIndexOf('➜'); // the "➜" in Hypixel titles
        if (arrow < 0) return false;
        String shown = lowerTitle.substring(arrow + 1).replace("...", "").trim();
        return shown.length() >= 8 && lowerName.startsWith(shown);
    }
}
