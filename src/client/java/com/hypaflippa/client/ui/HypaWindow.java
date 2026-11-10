package com.hypaflippa.client.ui;

import com.hypaflippa.HypaFlippaConfig;
import com.hypaflippa.client.Features;
import com.hypaflippa.client.MarketState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * The Hypa Flippa window: title bar, section bar (Flippa, Gamba), the tab bar of the open
 * section, dragging, resizing and the border. Tabs draw the content. There is one window shared by every host (bazaar overlay, other chest menus, the
 * standalone screen), so the open tab, scroll positions and selections carry over between them.
 *
 * Hosts forward mouse events; every handler returns true when the window used the event, so the
 * host knows not to pass it on to whatever is underneath. All coordinates are GUI-scaled pixels.
 */
public class HypaWindow {
    private static final int TITLE_BAR_HEIGHT = 20;
    private static final int SECTION_BAR_HEIGHT = 16;
    private static final int TAB_BAR_HEIGHT = 16;
    private static final int RESIZE_HANDLE_SIZE = 10;
    private static final int CLOSE_SIZE = 12;
    private static final int MIN_WIDTH = 260;
    // Enough for the Bazaar tab's three control rows plus its column picker.
    private static final int MIN_HEIGHT = 216;

    private enum Drag { NONE, MOVE, RESIZE }

    private final List<Section> sections;
    /** Every tab of every section, in order; activeTab indexes this list. */
    private final List<Tab> tabs = new ArrayList<>();
    /** The section index of each tab in {@link #tabs}. */
    private final List<Integer> sectionOf = new ArrayList<>();
    private int activeTab;

    // Set when a bazaar page switched tabs for us, so leaving the page can switch back.
    private int tabBeforeAutoFocus = -1;

    private Drag drag = Drag.NONE;
    private double dragStartMouseX, dragStartMouseY;
    private int dragStartX, dragStartY, dragStartWidth, dragStartHeight;
    // True while a mouse press that landed on the window is held down, so the matching
    // drag/release events are kept away from the screen underneath too.
    private boolean pressConsumed;
    private int screenWidth, screenHeight;

    public HypaWindow(List<Section> sections) {
        this.sections = sections;
        for (int s = 0; s < sections.size(); s++) {
            for (Tab tab : sections.get(s).tabs()) {
                tabs.add(tab);
                sectionOf.add(s);
            }
        }
        int saved = indexOf(config().activeTab);
        setActive(Math.max(0, saved));
    }

    private static HypaFlippaConfig config() {
        return Features.general;
    }

    private int indexOf(String tabId) {
        for (int i = 0; i < tabs.size(); i++) {
            if (tabs.get(i).id().equals(tabId)) return i;
        }
        return -1;
    }

    private Section activeSection() {
        return sections.get(sectionOf.get(activeTab));
    }

    public void showTab(String id) {
        int index = indexOf(id);
        if (index >= 0) selectTab(index);
    }

    /** The player picked a tab: remember it, also as the one to reopen in its section. */
    private void selectTab(int index) {
        tabBeforeAutoFocus = -1; // the player picked a tab themselves
        setActive(index);
        config().activeTab = tabs.get(index).id();
        config().lastTabs.put(activeSection().id(), tabs.get(index).id());
        config().save();
    }

    /** The player picked a section: open the tab they last had open there (else its first). */
    private void selectSection(int section) {
        if (section == sectionOf.get(activeTab)) return;
        int index = indexOf(config().lastTabs.getOrDefault(sections.get(section).id(), ""));
        if (index < 0 || sectionOf.get(index) != section) index = sectionOf.indexOf(section);
        selectTab(index);
    }

    private void setActive(int index) {
        activeTab = index;
        Features.show(tabs.get(index).feature()); // price history is fetched for what the open tab shows
    }

    /**
     * Called for every bazaar page. On a page a tab can explain (a Jerry Box, a gift, a dragon
     * fragment) switch to that tab; on any other page switch back to where the player was.
     */
    public void onBazaarPage(String title) {
        for (int i = 0; i < tabs.size(); i++) {
            if (tabs.get(i).focusBazaarPage(title)) {
                if (tabBeforeAutoFocus < 0) tabBeforeAutoFocus = activeTab;
                if (i != activeTab) setActive(i);
                return;
            }
        }
        if (tabBeforeAutoFocus >= 0) {
            tabs.forEach(Tab::clearFocus);
            int previous = tabBeforeAutoFocus;
            tabBeforeAutoFocus = -1;
            if (previous != activeTab) setActive(previous);
        }
    }

    /** Leaving the bazaar entirely: drop any automatic selection. */
    public void onBazaarClosed() {
        onBazaarPage("");
    }

    // --- Layout

    private Ui.Rect titleBar() { HypaFlippaConfig c = config(); return new Ui.Rect(c.windowX, c.windowY, c.windowWidth, TITLE_BAR_HEIGHT); }
    private Ui.Rect closeButton() { HypaFlippaConfig c = config(); return new Ui.Rect(c.windowX + c.windowWidth - CLOSE_SIZE - 4, c.windowY + 4, CLOSE_SIZE, CLOSE_SIZE); }
    private Ui.Rect refreshButton() { Ui.Rect close = closeButton(); return new Ui.Rect(close.x() - CLOSE_SIZE - 3, close.y(), CLOSE_SIZE, CLOSE_SIZE); }
    private Ui.Rect resizeHandle() { HypaFlippaConfig c = config(); return new Ui.Rect(c.windowX + c.windowWidth - RESIZE_HANDLE_SIZE, c.windowY + c.windowHeight - RESIZE_HANDLE_SIZE, RESIZE_HANDLE_SIZE, RESIZE_HANDLE_SIZE); }

    private Ui.Rect sectionButton(int index) {
        return barButton(index, sections.size(), config().windowY + TITLE_BAR_HEIGHT);
    }

    /** @param index the tab's place within the open section */
    private Ui.Rect tabButton(int index) {
        return barButton(index, activeSection().tabs().size(), config().windowY + TITLE_BAR_HEIGHT + SECTION_BAR_HEIGHT);
    }

    /** One of {@code count} buttons sharing a bar's width. */
    private Ui.Rect barButton(int index, int count, int barY) {
        HypaFlippaConfig c = config();
        int width = (c.windowWidth - 4) / count;
        return new Ui.Rect(c.windowX + 2 + index * width, barY + 1, width - 1, TAB_BAR_HEIGHT - 2);
    }

    private Ui.Rect contentArea() {
        HypaFlippaConfig c = config();
        int top = c.windowY + TITLE_BAR_HEIGHT + SECTION_BAR_HEIGHT + TAB_BAR_HEIGHT;
        return new Ui.Rect(c.windowX + 2, top, c.windowWidth - 4, c.windowY + c.windowHeight - 2 - top);
    }

    public boolean isMouseOver(double mx, double my) {
        HypaFlippaConfig c = config();
        return mx >= c.windowX && mx < c.windowX + c.windowWidth && my >= c.windowY && my < c.windowY + c.windowHeight;
    }

    /** Keeps the window inside the screen. Also repairs off-screen positions saved earlier. */
    private void clampToScreen() {
        if (screenWidth <= 0 || screenHeight <= 0) return;
        HypaFlippaConfig c = config();
        c.windowWidth = Math.clamp(c.windowWidth, MIN_WIDTH, Math.max(MIN_WIDTH, screenWidth));
        c.windowHeight = Math.clamp(c.windowHeight, MIN_HEIGHT, Math.max(MIN_HEIGHT, screenHeight));
        c.windowX = Math.clamp(c.windowX, 0, Math.max(0, screenWidth - c.windowWidth));
        c.windowY = Math.clamp(c.windowY, 0, Math.max(0, screenHeight - c.windowHeight));
    }

    // --- Input

    /** @param onClose what the X button does for this host (hide the overlay, close the screen) */
    public boolean mouseClicked(double mx, double my, int button, boolean shift, Runnable onClose) {
        pressConsumed = isMouseOver(mx, my);
        if (!pressConsumed) return false;
        // Any click on the window is swallowed so it never reaches a chest slot behind it.
        if (button == 1 && contentArea().contains(mx, my)) {
            tabs.get(activeTab).mouseRightClicked(contentArea(), mx, my);
            return true;
        }
        if (button != 0) return true;

        if (closeButton().contains(mx, my)) {
            onClose.run();
        } else if (refreshButton().contains(mx, my)) {
            MarketState.refreshAll();
        } else if (resizeHandle().contains(mx, my)) {
            startDrag(Drag.RESIZE, mx, my);
        } else if (titleBar().contains(mx, my)) {
            startDrag(Drag.MOVE, mx, my);
        } else if (contentArea().contains(mx, my)) {
            tabs.get(activeTab).mouseClicked(contentArea(), mx, my, shift);
        } else {
            for (int i = 0; i < sections.size(); i++) {
                if (sectionButton(i).contains(mx, my)) selectSection(i);
            }
            List<Tab> sectionTabs = activeSection().tabs();
            for (int i = 0; i < sectionTabs.size(); i++) {
                if (tabButton(i).contains(mx, my)) selectTab(tabs.indexOf(sectionTabs.get(i)));
            }
        }
        return true;
    }

    private void startDrag(Drag mode, double mx, double my) {
        HypaFlippaConfig c = config();
        drag = mode;
        dragStartMouseX = mx;
        dragStartMouseY = my;
        dragStartX = c.windowX;
        dragStartY = c.windowY;
        dragStartWidth = c.windowWidth;
        dragStartHeight = c.windowHeight;
    }

    /** mx/my are the current mouse position (MouseButtonEvent.x()/y()), not the drag delta. */
    public boolean mouseDragged(double mx, double my) {
        HypaFlippaConfig c = config();
        int dx = (int) Math.round(mx - dragStartMouseX);
        int dy = (int) Math.round(my - dragStartMouseY);
        if (drag == Drag.MOVE) {
            c.windowX = dragStartX + dx;
            c.windowY = dragStartY + dy;
            clampToScreen();
        } else if (drag == Drag.RESIZE) {
            // Cap at the screen edge first so growing past it doesn't shove the window left/up.
            c.windowWidth = Math.min(dragStartWidth + dx, screenWidth - c.windowX);
            c.windowHeight = Math.min(dragStartHeight + dy, screenHeight - c.windowY);
            clampToScreen();
        }
        return pressConsumed;
    }

    public boolean mouseReleased() {
        boolean consumed = pressConsumed;
        if (drag != Drag.NONE) {
            drag = Drag.NONE;
            config().save();
        }
        pressConsumed = false;
        return consumed;
    }

    public boolean mouseScrolled(double mx, double my, double deltaY) {
        if (!isMouseOver(mx, my)) return false;
        Ui.Rect content = contentArea();
        if (content.contains(mx, my)) tabs.get(activeTab).mouseScrolled(content, mx, my, deltaY);
        return true;
    }

    // --- Rendering

    public void render(GuiGraphicsExtractor guis, int mouseX, int mouseY) {
        screenWidth = guis.guiWidth();
        screenHeight = guis.guiHeight();
        clampToScreen();

        // Start a new layer so the window covers everything the host screen already drew,
        // including a chest's item icons and tooltips.
        guis.nextStratum();

        // No hover effects while dragging: the mouse is busy moving the window.
        if (drag != Drag.NONE) {
            mouseX = Integer.MIN_VALUE;
            mouseY = Integer.MIN_VALUE;
        }

        Font font = Minecraft.getInstance().font;
        HypaFlippaConfig c = config();
        int x = c.windowX, y = c.windowY, w = c.windowWidth, h = c.windowHeight;

        guis.fill(x, y, x + w, y + h, 0xCC000000);
        guis.outline(x, y, w, h, 0xFF444444);

        // Title bar
        guis.fill(x, y, x + w, y + TITLE_BAR_HEIGHT, 0xFF333333);
        guis.fill(x, y + TITLE_BAR_HEIGHT - 1, x + w, y + TITLE_BAR_HEIGHT, 0xFF666666);
        String title = "Hypa Flippa";
        guis.text(font, title, x + 6, y + 6, Ui.TEXT);

        Ui.Rect refresh = refreshButton();
        String status = statusText();
        int statusX = refresh.x() - 4 - font.width(status);
        if (statusX > x + 12 + font.width(title)) {
            guis.text(font, status, statusX, y + 6, Ui.TEXT_DIM);
        }
        Ui.button(guis, font, refresh, "↻", mouseX, mouseY); // ↻
        Ui.button(guis, font, closeButton(), "X", mouseX, mouseY);

        // Section bar, then the tab bar of the open section
        int sectionBarY = y + TITLE_BAR_HEIGHT;
        guis.fill(x, sectionBarY, x + w, sectionBarY + SECTION_BAR_HEIGHT + TAB_BAR_HEIGHT, Ui.CONTROLS_BG);
        int activeSection = sectionOf.get(activeTab);
        for (int i = 0; i < sections.size(); i++) {
            Ui.button(guis, font, sectionButton(i), sections.get(i).title(), mouseX, mouseY, i == activeSection);
        }
        List<Tab> sectionTabs = activeSection().tabs();
        for (int i = 0; i < sectionTabs.size(); i++) {
            Tab tab = sectionTabs.get(i);
            Ui.button(guis, font, tabButton(i), tab.title(), mouseX, mouseY, tab == tabs.get(activeTab));
        }

        Ui.Rect content = contentArea();
        tabs.get(activeTab).render(guis, font, content, mouseX, mouseY);

        Ui.Rect handle = resizeHandle();
        Ui.fill(guis, handle, 0xFF666666);
        guis.fill(handle.x(), handle.y(), handle.right() - 2, handle.bottom() - 2, 0xFF888888);
    }

    private static String statusText() {
        if (MarketState.isFetching()) return "Updating...";
        if (MarketState.error() != null) return "Fetch failed";
        if (MarketState.data() == null) return "";
        long seconds = (System.currentTimeMillis() - MarketState.lastFetchMs()) / 1000;
        return seconds + "s ago";
    }
}
