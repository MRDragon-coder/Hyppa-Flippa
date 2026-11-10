package com.hypaflippa.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.function.Supplier;

/** A "[-] label [+]" control for one numeric setting. */
public final class Stepper {
    public static final int BUTTON = 12;
    public static final int HEIGHT = 16;

    public interface StepAction {
        /** direction is -1 or +1; shift asks for a bigger step. */
        void step(int direction, boolean shift);
    }

    private final int labelWidth;
    private final Supplier<String> label;
    private final StepAction action;

    public Stepper(int labelWidth, Supplier<String> label, StepAction action) {
        this.labelWidth = labelWidth;
        this.label = label;
        this.action = action;
    }

    public int width() {
        return 2 * BUTTON + labelWidth;
    }

    /** The whole control: both buttons and the label between them. */
    public Ui.Rect bounds(int x, int y) {
        return new Ui.Rect(x, y, width(), HEIGHT);
    }

    private Ui.Rect minus(int x, int y) { return new Ui.Rect(x, y, BUTTON, HEIGHT); }
    private Ui.Rect plus(int x, int y) { return new Ui.Rect(x + BUTTON + labelWidth, y, BUTTON, HEIGHT); }

    public void render(GuiGraphicsExtractor guis, Font font, int x, int y, int mouseX, int mouseY) {
        Ui.button(guis, font, minus(x, y), "-", mouseX, mouseY);
        guis.centeredText(font, Ui.fit(font, label.get(), labelWidth), x + BUTTON + labelWidth / 2, y + 4, Ui.TEXT);
        Ui.button(guis, font, plus(x, y), "+", mouseX, mouseY);
    }

    /** @return true if the click hit one of the buttons (the value has then changed) */
    public boolean click(int x, int y, double mx, double my, boolean shift) {
        int direction = minus(x, y).contains(mx, my) ? -1 : plus(x, y).contains(mx, my) ? 1 : 0;
        if (direction == 0) return false;
        action.step(direction, shift);
        return true;
    }
}
