package cc.alpoyy.musicallymods.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/** Immediate-mode drawing helpers shared by the player and settings screens. */
public final class Ui {
    private Ui() {
    }

    /** Half-open rectangle; adjacent panels never share a clickable pixel. */
    public record Rect(int x, int y, int width, int height) {
        public Rect {
            width = Math.max(0, width);
            height = Math.max(0, height);
        }

        public int right() {
            return x + width;
        }

        public int bottom() {
            return y + height;
        }

        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < right() && mouseY >= y && mouseY < bottom();
        }

        public boolean intersects(Rect other) {
            return other != null && x < other.right() && right() > other.x()
                    && y < other.bottom() && bottom() > other.y();
        }

        /** Returns only the part of this rectangle inside {@code clip}. */
        public Rect intersection(Rect clip) {
            if (clip == null || !intersects(clip)) return new Rect(x, y, 0, 0);
            int left = Math.max(x, clip.x());
            int top = Math.max(y, clip.y());
            int right = Math.min(right(), clip.right());
            int bottom = Math.min(bottom(), clip.bottom());
            return new Rect(left, top, right - left, bottom - top);
        }
    }

    /** Scopes Minecraft's scissor state and restores it even on early returns. */
    public static final class Scissor implements AutoCloseable {
        private final GuiGraphics graphics;
        private final Rect rect;
        private boolean closed;

        private Scissor(GuiGraphics graphics, Rect rect) {
            this.graphics = graphics;
            this.rect = rect;
            graphics.enableScissor(rect.x(), rect.y(), rect.right(), rect.bottom());
        }

        public Rect rect() {
            return rect;
        }

        public boolean contains(double mouseX, double mouseY) {
            return rect.contains(mouseX, mouseY);
        }

        @Override
        public void close() {
            if (!closed) {
                graphics.disableScissor();
                closed = true;
            }
        }
    }

    public static Scissor scissor(GuiGraphics graphics, Rect rect) {
        return new Scissor(graphics, rect);
    }

    public static void panel(GuiGraphics graphics, Rect rect, int fill, int border) {
        if (rect.width() <= 0 || rect.height() <= 0) return;
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), fill);
        if (border != 0) graphics.renderOutline(rect.x(), rect.y(), rect.width(), rect.height(), border);
    }

    public static void button(GuiGraphics graphics, Font font, Rect rect, String label,
                              int fill, int border, int textColor, boolean enabled, boolean hovered) {
        if (rect.width() <= 0 || rect.height() <= 0) return;
        int background = enabled ? (hovered ? brighten(fill) : fill) : dim(fill);
        panel(graphics, rect, background, enabled && hovered ? textColor : border);
        int textWidth = Math.max(0, rect.width() - 8);
        graphics.drawCenteredString(font, fit(font, label, textWidth),
                rect.x() + rect.width() / 2, rect.y() + (rect.height() - font.lineHeight) / 2,
                enabled ? textColor : dim(textColor));
    }

    public static void label(GuiGraphics graphics, Font font, String text, int x, int y, int color) {
        graphics.drawString(font, text == null ? "" : text, x, y, color, false);
    }

    /** Truncates by Unicode code point so surrogate pairs are never split. */
    public static String fit(Font font, String text, int maxWidth) {
        if (text == null || text.isEmpty()) return "";
        if (maxWidth <= 0) return "";
        if (font.width(text) <= maxWidth) return text;
        if (font.width("...") > maxWidth) return "";
        int[] points = text.codePoints().toArray();
        int low = 0;
        int high = points.length;
        while (low < high) {
            int middle = (low + high + 1) / 2;
            if (font.width(join(points, middle) + "...") <= maxWidth) low = middle;
            else high = middle - 1;
        }
        return join(points, low) + "...";
    }

    public static List<String> wrap(Font font, String text, int maxWidth, int maxLines) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isBlank() || maxWidth <= 0 || maxLines <= 0) return result;
        for (String paragraph : text.split("\\R")) {
            String line = paragraph.trim();
            if (line.isEmpty()) {
                result.add("");
                continue;
            }
            while (!line.isEmpty() && font.width(line) > maxWidth && result.size() < maxLines) {
                int cut = line.length();
                while (cut > 0 && font.width(line.substring(0, cut)) > maxWidth) cut--;
                if (cut <= 0) cut = 1;
                result.add(line.substring(0, cut));
                line = line.substring(cut).trim();
            }
            if (!line.isEmpty() && result.size() < maxLines) result.add(line);
            if (result.size() >= maxLines) break;
        }
        return result;
    }

    /** Draws a compact scrollbar only when there is something to scroll. */
    public static void scrollbar(GuiGraphics graphics, Rect track, int scroll, int maxScroll,
                                 int viewportSize, int contentSize, int barColor, int thumbColor) {
        if (maxScroll <= 0 || track.width() <= 2 || track.height() <= 8) return;
        graphics.fill(track.x(), track.y(), track.right(), track.bottom(), barColor);
        int thumbHeight = Math.max(12, Math.min(track.height(),
                (int) (track.height() * Math.min(1f, viewportSize / (float) Math.max(1, contentSize)))));
        int travel = Math.max(0, track.height() - thumbHeight);
        int thumbY = track.y() + (int) (travel * Math.max(0f, Math.min(1f, scroll / (float) maxScroll)));
        graphics.fill(track.x() + 1, thumbY, track.right() - 1, thumbY + thumbHeight, thumbColor);
    }

    public static int clampScroll(int value, int max) {
        return Math.max(0, Math.min(Math.max(0, max), value));
    }

    public static String formatTime(long millis) {
        long safe = Math.max(0L, millis);
        long seconds = safe / 1000L;
        return String.format("%02d:%02d", seconds / 60L, seconds % 60L);
    }

    public static String colorLabel(int argb) {
        return String.format("#%08X", argb);
    }

    public static int withAlpha(int argb, int alpha) {
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    private static String join(int[] points, int count) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < count; i++) builder.appendCodePoint(points[i]);
        return builder.toString();
    }

    private static int brighten(int color) {
        int r = Math.min(255, ((color >> 16) & 0xFF) + 24);
        int g = Math.min(255, ((color >> 8) & 0xFF) + 24);
        int b = Math.min(255, (color & 0xFF) + 24);
        return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    private static int dim(int color) {
        int r = ((color >> 16) & 0xFF) * 55 / 100;
        int g = ((color >> 8) & 0xFF) * 55 / 100;
        int b = (color & 0xFF) * 55 / 100;
        return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
    }
}
