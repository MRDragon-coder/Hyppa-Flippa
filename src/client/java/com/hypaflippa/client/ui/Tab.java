package com.hypaflippa.client.ui;

import com.hypaflippa.client.Feature;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * One page of the Hypa Flippa window. The window draws the frame (title, tabs, border) and hands
 * each tab the content area below the tab bar; the tab lays out and draws everything inside it.
 */
public interface Tab {
    /** Stable id, stored in the config to reopen the last tab. */
    String id();

    /** Short name shown on the tab button. */
    String title();

    /** The feature whose results this tab shows, or null when it computes its own. */
    default Feature feature() {
        return null;
    }

    void render(GuiGraphicsExtractor guis, Font font, Ui.Rect area, int mouseX, int mouseY);

    /** A left click inside the content area. */
    void mouseClicked(Ui.Rect area, double mx, double my, boolean shift);

    /** A right click inside the content area. */
    default void mouseRightClicked(Ui.Rect area, double mx, double my) {}

    void mouseScrolled(Ui.Rect area, double mx, double my, double deltaY);

    /**
     * If this tab can show the item whose bazaar page has this title, select it and return true.
     * Used to jump to the right calculation when you open, say, a Jerry Box page in the bazaar.
     */
    default boolean focusBazaarPage(String title) {
        return false;
    }

    /** Undo {@link #focusBazaarPage} when the player leaves that page. */
    default void clearFocus() {}
}
