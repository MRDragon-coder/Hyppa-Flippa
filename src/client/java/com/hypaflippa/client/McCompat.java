package com.hypaflippa.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Every Minecraft-version difference in the mod lives here, so the rest of the code is
 * identical for all versions.
 *
 * The {@code //?} comments are Stonecutter conditions: when building for a version, Stonecutter
 * comments out the branch that doesn't apply and uncomments the one that does. Leave them alone
 * when editing; switch the version with the "Set active project to ..." Gradle tasks.
 */
public final class McCompat {
    private McCompat() {}

    /** In 26.2 Mojang moved the current screen from Minecraft to Minecraft.gui. */
    public static Screen currentScreen(Minecraft client) {
        //? if >=26.2 {
        return client.gui.screen();
        //?} else
        /*return client.screen;*/
    }

    public static void setScreen(Minecraft client, Screen screen) {
        //? if >=26.2 {
        client.gui.setScreen(screen);
        //?} else
        /*client.setScreen(screen);*/
    }
}
