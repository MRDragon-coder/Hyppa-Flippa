package com.hypaflippa.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * The rows of settings at the top of a tab: steppers and buttons at fixed spots, each with a hover
 * help. Positions are relative to the tab's content area; row 0 is the top row.
 */
public final class Controls {
    public static final int ROW_HEIGHT = 20;
    private static final BooleanSupplier ALWAYS = () -> true;

    private interface Control {
        boolean visible();
        Ui.Rect bounds(Ui.Rect area);
        void render(GuiGraphicsExtractor guis, Font font, Ui.Rect area, int mouseX, int mouseY);
        /** @return true if the click changed a setting */
        boolean click(Ui.Rect area, double mx, double my, boolean shift);
        Ui.Help help();
    }

    private final int rows;
    private final Runnable onChange;
    private final List<Control> controls = new ArrayList<>();

    /**
     * @param rows     how many rows of controls there are
     * @param onChange runs after any control changed a setting (save, recompute)
     */
    public Controls(int rows, Runnable onChange) {
        this.rows = rows;
        this.onChange = onChange;
    }

    /** The height of the controls band, including its bottom margin. */
    public int height() {
        return rows * ROW_HEIGHT + 2;
    }

    private static int rowY(Ui.Rect area, int row) {
        return area.y() + 3 + row * ROW_HEIGHT;
    }

    public Controls stepper(int row, int x, Stepper stepper, Supplier<Ui.Help> help) {
        return stepper(row, x, stepper, help, ALWAYS);
    }

    /** A stepper; its help also says that shift makes bigger steps. */
    public Controls stepper(int row, int x, Stepper stepper, Supplier<Ui.Help> help, BooleanSupplier visible) {
        controls.add(new Control() {
            public boolean visible() { return visible.getAsBoolean(); }
            public Ui.Rect bounds(Ui.Rect area) { return stepper.bounds(area.x() + x, rowY(area, row)); }
            public void render(GuiGraphicsExtractor guis, Font font, Ui.Rect area, int mouseX, int mouseY) {
                stepper.render(guis, font, area.x() + x, rowY(area, row), mouseX, mouseY);
            }
            public boolean click(Ui.Rect area, double mx, double my, boolean shift) {
                return stepper.click(area.x() + x, rowY(area, row), mx, my, shift);
            }
            public Ui.Help help() { return help.get().plus(Ui.STEP_HINT); }
        });
        return this;
    }

    public Controls button(int row, int x, int width, Supplier<String> label, BooleanSupplier active,
                           Supplier<Ui.Help> help, Runnable action) {
        return button(row, x, width, label, active, help, action, ALWAYS);
    }

    /** @param active draws it highlighted (an option that's on, the open page) */
    public Controls button(int row, int x, int width, Supplier<String> label, BooleanSupplier active,
                           Supplier<Ui.Help> help, Runnable action, BooleanSupplier visible) {
        controls.add(new Control() {
            public boolean visible() { return visible.getAsBoolean(); }
            public Ui.Rect bounds(Ui.Rect area) { return new Ui.Rect(area.x() + x, rowY(area, row), width, 16); }
            public void render(GuiGraphicsExtractor guis, Font font, Ui.Rect area, int mouseX, int mouseY) {
                Ui.button(guis, font, bounds(area), label.get(), mouseX, mouseY, active.getAsBoolean());
            }
            public boolean click(Ui.Rect area, double mx, double my, boolean shift) {
                if (!bounds(area).contains(mx, my)) return false;
                action.run();
                return true;
            }
            public Ui.Help help() { return help.get(); }
        });
        return this;
    }

    /** Draws the band's background and every visible control. */
    public void render(GuiGraphicsExtractor guis, Font font, Ui.Rect area, int mouseX, int mouseY) {
        guis.fill(area.x() - 2, area.y(), area.right() + 2, area.y() + height(), Ui.CONTROLS_BG);
        for (Control control : controls) {
            if (control.visible()) control.render(guis, font, area, mouseX, mouseY);
        }
    }

    /** @return true if a control took the click */
    public boolean click(Ui.Rect area, double mx, double my, boolean shift) {
        for (Control control : controls) {
            if (control.visible() && control.click(area, mx, my, shift)) {
                onChange.run();
                return true;
            }
        }
        return false;
    }

    /** The help of the control under the mouse, or null. */
    public Ui.Help helpAt(Ui.Rect area, double mx, double my) {
        for (Control control : controls) {
            if (control.visible() && control.bounds(area).contains(mx, my)) return control.help();
        }
        return null;
    }
}
