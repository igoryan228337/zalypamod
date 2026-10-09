package dev.musicplayer.ui;

import net.minecraft.client.gui.GuiGraphics;

/** Kept as a separate hook point for screen-specific interaction/edit mode. */
public final class ScreenOverlay {
    private ScreenOverlay() {}
    public static void init() {}
    public static void render(GuiGraphics graphics) { MusicHud.render(graphics); }
}