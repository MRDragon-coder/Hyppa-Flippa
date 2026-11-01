package com.hypaflippa.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

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
