package cc.alpoyy.musicallymods.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;

import java.util.ArrayList;
import java.util.List;

/** Tiny immediate-mode drawing helpers shared by the player and settings screens. */
public final class Ui {
    private Ui() {
    }

    public record Rect(int x, int y, int width, int height) {
        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }

    public static void panel(GuiGraphics graphics, Rect rect, int fill, int border) {
        graphics.fill(rect.x(), rect.y(), rect.x() + rect.width(), rect.y() + rect.height(), fill);
        if (border != 0) graphics.renderOutline(rect.x(), rect.y(), rect.width(), rect.height(), border);
    }

    public static void button(GuiGraphics graphics, Font font, Rect rect, String label,
                              int fill, int border, int textColor, boolean enabled, boolean hovered) {
        int background = enabled ? (hovered ? brighten(fill) : fill) : dim(fill);
        panel(graphics, rect, background, enabled && hovered ? textColor : border);
        graphics.drawCenteredString(font, fit(font, label, rect.width() - 8),
                rect.x() + rect.width() / 2, rect.y() + (rect.height() - font.lineHeight) / 2,
                enabled ? textColor : dim(textColor));
    }

    public static void label(GuiGraphics graphics, Font font, String text, int x, int y, int color) {
        graphics.drawString(font, text, x, y, color, false);
    }

    public static String fit(Font font, String text, int maxWidth) {
        if (text == null) return "";
        if (font.width(text) <= maxWidth) return text;
        String candidate = text;
        while (!candidate.isEmpty() && font.width(candidate + "...") > maxWidth) {
            candidate = candidate.substring(0, candidate.length() - 1);
        }
        return candidate + "...";
    }

    public static List<String> wrap(Font font, String text, int maxWidth, int maxLines) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isBlank() || maxWidth <= 0) return result;
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

    public static String formatTime(long millis) {
        if (millis < 0) millis = 0;
        long seconds = millis / 1000L;
        long minutes = seconds / 60L;
        return String.format("%02d:%02d", minutes, seconds % 60L);
    }

    public static String colorLabel(int argb) {
        return String.format("#%08X", argb);
    }

    public static int withAlpha(int argb, int alpha) {
        return (alpha << 24) | (argb & 0x00FFFFFF);
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
