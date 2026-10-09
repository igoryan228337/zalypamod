package dev.musicplayer.ui;

import dev.musicplayer.client.MusicPlayerClient;
import dev.musicplayer.library.Track;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import java.util.List;

/** Rich queue panel rendered directly below the player HUD. */
public final class QueueHud {
    private static boolean open;
    private static final int ROW_H = 42;
    private static final int HEADER_H = 30;
    private static final int PANEL_W = 390;
    private static final int MAX_ROWS = 9;
    private static int scroll;

    private QueueHud() {}

    public static void toggle() { open = !open; if (!open) scroll = 0; clampScroll(MusicPlayerClient.get()); }
    public static boolean open() { return open; }
    public static void close() { open = false; scroll = 0; }
    public static int scroll() { return scroll; }

    public static void scrollBy(int delta) {
        MusicPlayerClient m = MusicPlayerClient.get();
        int max = Math.max(0, m.QUEUE.items().size() - MAX_ROWS);
        scroll = Math.max(0, Math.min(max, scroll + delta));
    }

    public static void clampScroll(MusicPlayerClient m) {
        int max = Math.max(0, m.QUEUE.items().size() - MAX_ROWS);
        scroll = Math.max(0, Math.min(max, scroll));
    }

    public static Bounds bounds(MusicPlayerClient m, int sw, int sh) {
        clampScroll(m);
        int pw = Math.min(PANEL_W, Math.max(260, sw - 8));
        int x = Math.max(4, Math.min(m.CONFIG.x, sw - pw - 4));
        int rows = Math.min(MAX_ROWS, m.QUEUE.items().size());
        int ph = HEADER_H + Math.max(1, rows) * ROW_H + 8;
        int y = Math.max(4, Math.min(m.CONFIG.y + Math.max(76, m.CONFIG.height) + 6, sh - ph - 4));
        return new Bounds(x, y, pw, ph);
    }

    public static int indexAt(MusicPlayerClient m, double mx, double my) {
        Bounds b = bounds(m, Minecraft.getInstance().getWindow().getGuiScaledWidth(), Minecraft.getInstance().getWindow().getGuiScaledHeight());
        if (!b.contains(mx, my) || my < b.y + HEADER_H || my >= b.y + HEADER_H + MAX_ROWS * ROW_H) return -1;
        int visible = (int)((my - b.y - HEADER_H) / ROW_H);
        int i = scroll + visible;
        return i >= 0 && i < m.QUEUE.items().size() ? i : -1;
    }

    public static boolean deleteAt(MusicPlayerClient m, double mx, double my) {
        int i = indexAt(m, mx, my); if (i < 0) return false;
        Bounds b = bounds(m, Minecraft.getInstance().getWindow().getGuiScaledWidth(), Minecraft.getInstance().getWindow().getGuiScaledHeight());
        int rowX = b.x, rowY = b.y + HEADER_H + (i - scroll) * ROW_H;
        if (mx >= rowX + b.w - 28 && mx <= rowX + b.w - 6 && my >= rowY + 9 && my <= rowY + 31) {
            m.removeQueue(i); clampScroll(m); return true;
        }
        return false;
    }

    public static boolean playNextAt(MusicPlayerClient m, double mx, double my) {
        int i = indexAt(m, mx, my); if (i < 0) return false;
        Bounds b = bounds(m, Minecraft.getInstance().getWindow().getGuiScaledWidth(), Minecraft.getInstance().getWindow().getGuiScaledHeight());
        int rowX = b.x, rowY = b.y + HEADER_H + (i - scroll) * ROW_H;
        if (mx >= rowX + b.w - 56 && mx < rowX + b.w - 30 && my >= rowY + 9 && my <= rowY + 31) {
            m.queueNext(m.QUEUE.items().get(i)); return true;
        }
        return false;
    }

    public static void render(GuiGraphics g) {
        if (!open) return;
        MusicPlayerClient m = MusicPlayerClient.get();
        if (m.CONFIG == null || !m.CONFIG.visible) return;
        clampScroll(m);
        int sw = g.guiWidth(), sh = g.guiHeight();
        Bounds b = bounds(m, sw, sh);
        var font = Minecraft.getInstance().font;
        int bg = 0xF012141A, panel = 0xFF1B1E27, line = 0xFF363A45, text = 0xFFF2F2F4, secondary = 0xFFA7ABB6, accent = 0xFF8AB4FF;
        g.fill(b.x, b.y, b.x + b.w, b.y + b.h, bg);
        g.fill(b.x, b.y, b.x + b.w, b.y + 1, line);
        g.drawString(font, "Queue", b.x + 11, b.y + 9, text, true);
        String counter = m.QUEUE.items().isEmpty() ? "0 tracks" : (scroll + 1) + "–" + Math.min(scroll + MAX_ROWS, m.QUEUE.items().size()) + " / " + m.QUEUE.items().size();
        g.drawString(font, counter, b.x + b.w - font.width(counter) - 10, b.y + 9, secondary, false);
        List<Track> items = m.QUEUE.items();
        int current = m.QUEUE.index();
        int end = Math.min(items.size(), scroll + MAX_ROWS);
        for (int i = scroll; i < end; i++) {
            int row = i - scroll, y = b.y + HEADER_H + row * ROW_H;
            if (i == current) g.fill(b.x + 5, y + 2, b.x + b.w - 5, y + ROW_H - 2, 0x553B6EA8);
            Track t = items.get(i);
            int cover = 30;
            if (t.artwork != null) {
                var id = TextureCache.get(t.artwork);
                if (id != null) g.blit(id, b.x + 9, y + 6, cover, cover, 0, 0, 1, 1, 1, 1);
            } else g.fill(b.x + 9, y + 6, b.x + 9 + cover, y + 6 + cover, 0xFF292C34);
            int tx = b.x + 47;
            String title = (i + 1) + ". " + t.title;
            if (title.length() > 38) title = title.substring(0, 35) + "...";
            String artist = t.displayArtist();
            if (artist.length() > 38) artist = artist.substring(0, 35) + "...";
            g.drawString(font, title, tx, y + 7, i == current ? accent : text, false);
            g.drawString(font, artist, tx, y + 21, secondary, false);
            g.drawString(font, formatTime(t.durationMs), tx, y + 31, secondary, false);
            g.fill(b.x + b.w - 56, y + 10, b.x + b.w - 30, y + 31, panel);
            g.drawString(font, ">", b.x + b.w - 47, y + 14, accent, true);
            g.fill(b.x + b.w - 28, y + 10, b.x + b.w - 6, y + 31, panel);
            g.drawString(font, "×", b.x + b.w - 22, y + 13, 0xFFE26D7D, true);
        }
        if (items.isEmpty()) g.drawString(font, "Queue is empty", b.x + 12, b.y + HEADER_H + 13, secondary, false);
        if (items.size() > MAX_ROWS) {
            int trackX = b.x + b.w - 3, trackTop = b.y + HEADER_H + 3, trackBottom = b.y + b.h - 7;
            g.fill(trackX, trackTop, trackX + 2, trackBottom, 0x553A3D45);
            int max = items.size() - MAX_ROWS, thumbH = Math.max(16, (trackBottom-trackTop) * MAX_ROWS / items.size());
            int thumbY = trackTop + (trackBottom-trackTop-thumbH) * scroll / Math.max(1, max);
            g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, accent);
        }
    }

    private static String formatTime(long ms) { long s = Math.max(0, ms / 1000); return String.format(java.util.Locale.ROOT, "%d:%02d", s / 60, s % 60); }
    public record Bounds(int x, int y, int w, int h) { public boolean contains(double mx, double my) { return mx >= x && mx <= x+w && my >= y && my <= y+h; } }
}
