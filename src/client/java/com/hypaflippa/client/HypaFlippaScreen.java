package com.hypaflippa.client;

import com.hypaflippa.client.ui.HypaWindow;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * The window on its own, outside any menu: opened by the toggle key, /hypaflippa or /flips. All
 * the real work lives in {@link HypaWindow}; this screen just hosts it and forwards input.
 */
public class HypaFlippaScreen extends Screen {
    public HypaFlippaScreen() {
        super(Component.literal("Hypa Flippa"));
    }

    private static HypaWindow window() {
        return HypaFlippaClient.window();
    }

    @Override
    public void tick() {
        super.tick();
        MarketState.refreshIfStale();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // The same key that opened the window closes it again.
        if (HypaFlippaClient.toggleKey().matches(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return window().mouseClicked(event.x(), event.y(), event.button(), event.hasShiftDown(), this::onClose)
                || super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        // dragX/dragY are how far the mouse moved, not where it is; the window needs the position.
        return window().mouseDragged(event.x(), event.y()) || super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return window().mouseReleased() || super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        return window().mouseScrolled(mouseX, mouseY, deltaY) || super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor guis, int mouseX, int mouseY, float partialTick) {
        // No dimmed background: keep the world visible around the window.
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guis, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(guis, mouseX, mouseY, partialTick);
        window().render(guis, mouseX, mouseY);
    }
}
