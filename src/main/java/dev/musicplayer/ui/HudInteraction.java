package dev.musicplayer.ui;

import dev.musicplayer.client.MusicPlayerClient;
import dev.musicplayer.library.Track;
import dev.musicplayer.theme.Theme;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** Mouse interaction for the always-on-top music HUD and its queue. */
public final class HudInteraction {
    private static boolean wasLeft, wasRight;
    private static int queueDragIndex = -1, queueHoverIndex = -1;
    private static boolean queueMoved;
    private static boolean dragging, resizing;
    private static double grabX, grabY;
    private static int startX, startY, startW, startH;

    private HudInteraction() {}

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        MusicPlayerClient mod = MusicPlayerClient.get();
        if (mod.CONFIG == null || !mod.CONFIG.visible || mc.getWindow() == null) {
            reset(); return;
        }
        long window = mc.getWindow().getWindow();
        double[] cx = new double[1], cy = new double[1];
        GLFW.glfwGetCursorPos(window, cx, cy);
        double sx = (double) mc.getWindow().getGuiScaledWidth() / Math.max(1, mc.getWindow().getWidth());
        double sy = (double) mc.getWindow().getGuiScaledHeight() / Math.max(1, mc.getWindow().getHeight());
        double mx = cx[0] * sx, my = cy[0] * sy;
        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        int w = Math.max(220, mod.CONFIG.width), h = Math.max(76, mod.CONFIG.height);
        int x = Math.max(4, Math.min(mod.CONFIG.x, sw - w - 4));
        int y = Math.max(4, Math.min(mod.CONFIG.y, sh - h - 4));
        boolean inside = mx >= x && mx <= x + w && my >= y && my <= y + h;
        boolean left = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean right = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        boolean pressed = left && !wasLeft;
        boolean rightPressed = right && !wasRight;

        if (rightPressed && inside) QueueHud.toggle();

        if (QueueHud.open()) {
            QueueHud.Bounds qb = QueueHud.bounds(mod, sw, sh);
            if (pressed && qb.contains(mx, my)) {
                if (QueueHud.deleteAt(mod, mx, my) || QueueHud.playNextAt(mod, mx, my)) {
                    wasLeft = left; wasRight = right; return;
                }
                int hit = QueueHud.indexAt(mod, mx, my);
                if (hit >= 0) {
                    queueDragIndex = hit;
                    queueHoverIndex = hit;
                    queueMoved = false;
                }
            }
            if (left && queueDragIndex >= 0) {
                queueHoverIndex = QueueHud.indexAt(mod, mx, my);
                if (my < qb.y() + 40) { QueueHud.scrollBy(-1); queueHoverIndex = QueueHud.indexAt(mod, mx, my); }
                else if (my > qb.y() + qb.h() - 30) { QueueHud.scrollBy(1); queueHoverIndex = QueueHud.indexAt(mod, mx, my); }
                if (queueHoverIndex >= 0 && queueHoverIndex != queueDragIndex) {
                    mod.moveQueue(queueDragIndex, queueHoverIndex);
                    queueDragIndex = queueHoverIndex;
                    queueMoved = true;
                }
            }
            if (!left && wasLeft && queueDragIndex >= 0) {
                if (!queueMoved && queueHoverIndex == queueDragIndex) {
                    Track t = mod.QUEUE.items().get(queueDragIndex);
                    mod.play(t);
                }
                queueDragIndex = -1; queueHoverIndex = -1; queueMoved = false;
            }
        }

        Theme theme = mod.theme();
        Theme.Layer progress = find(theme, "progress", "progress");
        Theme.Layer controls = find(theme, "controls", "controls");
        Theme.Layer volume = find(theme, "volume", "volume");

        if (pressed && inside) {
            double lx = mx - x, ly = my - y;
            if (hit(progress, lx, ly)) seek(mod, progress, lx);
            else if (hit(volume, lx, ly)) setVolume(mod, volume, lx);
            else if (hit(controls, lx, ly)) {
                double third = controls.width / 3.0;
                if (lx < controls.x + third) mod.previous();
                else if (lx < controls.x + third * 2) mod.togglePlay();
                else mod.next();
            } else if (lx >= w - 18 && ly >= h - 18) {
                resizing = true; startX = mod.CONFIG.x; startY = mod.CONFIG.y; startW = mod.CONFIG.width; startH = mod.CONFIG.height; grabX = mx; grabY = my;
            } else {
                dragging = true; startX = mod.CONFIG.x; startY = mod.CONFIG.y; grabX = mx; grabY = my;
            }
        }
        if (left && (dragging || resizing)) {
            double dx = mx - grabX, dy = my - grabY;
            if (resizing) {
                mod.CONFIG.width = Math.max(220, Math.min(sw - 8, startW + (int)dx));
                mod.CONFIG.height = Math.max(76, Math.min(sh - 8, startH + (int)dy));
            } else {
                mod.CONFIG.x = Math.max(4, Math.min(sw - mod.CONFIG.width - 4, startX + (int)dx));
                mod.CONFIG.y = Math.max(4, Math.min(sh - mod.CONFIG.height - 4, startY + (int)dy));
            }
        }
        if (!left && wasLeft) {
            if (dragging || resizing) mod.saveConfig();
            dragging = false; resizing = false;
        }
        wasLeft = left; wasRight = right;
    }

    private static void reset() { wasLeft = false; wasRight = false; dragging = false; resizing = false; queueDragIndex = -1; queueHoverIndex = -1; queueMoved = false; }
    private static Theme.Layer find(Theme theme, String id, String kind) { if (theme == null || theme.layers == null) return null; for (Theme.Layer l : theme.layers) if (l != null && (id.equalsIgnoreCase(l.id) || kind.equalsIgnoreCase(l.kind))) return l; return null; }
    private static boolean hit(Theme.Layer l, double x, double y) { return l != null && x >= l.x && y >= l.y && x <= l.x + l.width && y <= l.y + l.height; }
    private static void seek(MusicPlayerClient mod, Theme.Layer l, double localX) { double f = Math.max(0, Math.min(1, (localX-l.x)/Math.max(1,l.width))); mod.seek((long)(Math.max(0,mod.AUDIO.durationMs())*f)); }
    private static void setVolume(MusicPlayerClient mod, Theme.Layer l, double localX) { mod.setVolume((float)Math.max(0,Math.min(1,(localX-l.x)/Math.max(1,l.width)))); }
}
