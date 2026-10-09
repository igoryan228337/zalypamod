package dev.musicplayer.ui;
import dev.musicplayer.client.MusicPlayerClient;import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;import net.minecraft.client.gui.GuiGraphics;
/** Kept as a separate hook point for screen-specific interaction/edit mode. */
public final class ScreenOverlay{public static void init(){} public static void render(GuiGraphics g){MusicHud.render(g);}}
