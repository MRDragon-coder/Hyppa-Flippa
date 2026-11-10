package com.hypaflippa.client;

import com.hypaflippa.client.ui.HypaWindow;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;

import java.util.Locale;

/**
 * Draws the Hypa Flippa window on top of chest menus without replacing them. It shows by itself
 * in the bazaar, and in any other menu (auction house, inventory, ...) when the toggle key is
 * pressed.
 *
 * Instead of opening our own Screen, we hook each chest screen's Fabric events: render the window
 * after the chest has drawn, and veto mouse events that land on the window so they never click
 * the slots behind it. Fabric recreates these per-screen events on every init/resize, so
 * registering them in AFTER_INIT never double-registers.
 */
public final class WindowOverlay {
    // True from the moment a "Bazaar" chest opens until no chest is open at all. Hypixel
    // opens a new chest screen for every bazaar page, and item pages are titled after the
    // item rather than "Bazaar", so the title alone can't tell us we're still in the bazaar.
    private static boolean inBazaar = false;
    // X (or the key) pressed while in the bazaar; stays hidden until the bazaar is left.
    private static boolean hiddenInBazaar = false;
    // Toggle key pressed in a chest menu; stays shown until closed or no menu is open.
    private static boolean openedByKey = false;

    private WindowOverlay() {}

    private static HypaWindow window() {
        return HypaFlippaClient.window();
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) -> {
            if (!(screen instanceof AbstractContainerScreen<?>)) return;
            String title = screen.getTitle().getString();
            if (title.toLowerCase(Locale.ROOT).contains("bazaar")) {
                inBazaar = true;
            }
            attach(screen);
            // Any chest that replaces a bazaar chest is a bazaar page too.
            if (inBazaar) {
                window().onBazaarPage(title);
            }
        });

        // Checked at the end of the tick, so a close + reopen in the same tick (page switch)
        // doesn't count as leaving.
        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
            if (!(McCompat.currentScreen(minecraft) instanceof AbstractContainerScreen<?>)) {
                if (inBazaar) window().onBazaarClosed();
                inBazaar = false;
                hiddenInBazaar = false;
                openedByKey = false;
            }
        });
    }

    private static boolean visible() {
        return openedByKey || (inBazaar && !hiddenInBazaar);
    }

    private static void close() {
        openedByKey = false;
        if (inBazaar) hiddenInBazaar = true;
    }

    private static void toggle() {
        if (visible()) {
            close();
        } else {
            openedByKey = true;
        }
    }

    private static void attach(Screen screen) {
        ScreenEvents.afterExtract(screen).register((s, guis, mouseX, mouseY, partialTick) -> {
            if (visible()) window().render(guis, mouseX, mouseY);
        });
        ScreenEvents.afterTick(screen).register(s -> {
            if (visible()) MarketState.refreshIfStale();
        });

        // The creative inventory has a search box; typing there must not toggle the window.
        if (!(screen instanceof CreativeModeInventoryScreen)) {
            ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) -> {
                if (!HypaFlippaClient.toggleKey().matches(event)) return true;
                toggle();
                return false;
            });
        }

        // "allow" events: returning false cancels the event for the chest screen.
        // Drag and release skip the visible check: they only claim events whose press the
        // window took, which also covers the release right after clicking X.
        ScreenMouseEvents.allowMouseClick(screen).register((s, event) ->
                !visible() || !window().mouseClicked(event.x(), event.y(), event.button(), event.hasShiftDown(), WindowOverlay::close));
        ScreenMouseEvents.allowMouseDrag(screen).register((s, event, dragX, dragY) ->
                !window().mouseDragged(event.x(), event.y()));
        ScreenMouseEvents.allowMouseRelease(screen).register((s, event) ->
                !window().mouseReleased());
        ScreenMouseEvents.allowMouseScroll(screen).register((s, mouseX, mouseY, horizontal, vertical) ->
                !visible() || !window().mouseScrolled(mouseX, mouseY, vertical));
    }
}
