package cc.alpoyy.musicallymods.hud;

import cc.alpoyy.musicallymods.audio.AudioManager;
import cc.alpoyy.musicallymods.audio.PlaybackSnapshot;
import cc.alpoyy.musicallymods.config.ModConfig;
import cc.alpoyy.musicallymods.gui.Ui;
import cc.alpoyy.musicallymods.lyrics.LyricLine;
import cc.alpoyy.musicallymods.lyrics.LyricsData;
import cc.alpoyy.musicallymods.lyrics.LyricsManager;
import cc.alpoyy.musicallymods.music.Track;
import cc.alpoyy.musicallymods.util.ArtworkCache;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/** Independent lyrics overlay. Highlights lines only when LRCLIB supplied real timestamps. */
public final class LyricsHud implements HudElement {
    @Override
    public void render(GuiGraphics graphics, DeltaTracker tickCounter) {
        Minecraft client = Minecraft.getInstance();
        ModConfig config = ModConfig.get();
        ModConfig.Lyrics cfg = config.lyrics;
        if (!cfg.enabled || client.options.hideGui) return;

        LyricsManager lyricsManager = LyricsManager.get();
        LyricsData data = lyricsManager.current();
        String status = lyricsManager.isLoading()
                ? "Lyrics: searching a public lyrics database..."
                : data.error();
        if (data.isEmpty() && status.isBlank()) return;

        int guiWidth = Math.max(1, graphics.guiWidth());
        int guiHeight = Math.max(1, graphics.guiHeight());
        float scale = Math.max(0.5f, Math.min(2f, cfg.scale));
        int width = Math.max(1, Math.min(cfg.width, guiWidth));
        int height = Math.max(1, Math.min(cfg.height, guiHeight));
        int scaledWidth = Math.max(1, Math.min(guiWidth, (int) Math.ceil(width * scale)));
        int scaledHeight = Math.max(1, Math.min(guiHeight, (int) Math.ceil(height * scale)));
        int x = clamp(positionX(cfg.position, guiWidth, scaledWidth, cfg.manualX),
                0, guiWidth - scaledWidth);
        int y = clamp(positionY(cfg.position, guiHeight, scaledHeight, cfg.manualY),
                0, guiHeight - scaledHeight);
        int alphaBg = Ui.withAlpha(config.colors.lyricsBg, cfg.transparency);

        graphics.pose().pushMatrix();
        try (Ui.Scissor ignored = Ui.scissor(graphics, new Ui.Rect(x, y, scaledWidth, scaledHeight))) {
            if (scale != 1f) scaleAround(graphics, x, y, scale);
            graphics.fill(x, y, x + width, y + height, alphaBg);
            graphics.renderOutline(x, y, width, height, config.colors.accent);
            if (data.isEmpty()) {
                renderStatus(graphics, client, status, x, y, width, height, cfg, config);
                return;
            }

            Track track = lyricsManager.currentTrack();
            PlaybackSnapshot snapshot = AudioManager.get().snapshot();
            int padding = clamp(cfg.padding, 0, Math.max(0, Math.min(width, height) / 4));
            int textX = x + padding;
            int textY = y + padding;
            int textWidth = width - padding * 2;
            float fontScale = Math.max(0.7f, cfg.fontSize);
            if (cfg.showArtwork && track != null && !track.artworkUrl().isBlank()) {
                ArtworkCache.request(track);
                int size = Math.max(0, Math.min(48, height - padding * 2));
                if (size > 0) {
                    if (ArtworkCache.has(track)) {
                        ArtworkCache.render(graphics, track, textX, textY, size, size);
                    } else {
                        graphics.fill(textX, textY, textX + size, textY + size, config.colors.controls);
                    }
                    textX += size + padding;
                    textWidth -= size + padding;
                }
            }

            if (textWidth <= 0 || textX >= x + width || textY >= y + height) return;

            graphics.pose().pushMatrix();
            try {
                if (fontScale != 1f) scaleAround(graphics, textX, textY, fontScale);
                int drawableWidth = Math.max(1, (int) (textWidth / fontScale));
                int lineHeight = client.font.lineHeight + cfg.lineSpacing;

                if (cfg.showTitle) {
                    graphics.drawString(client.font, Ui.fit(client.font, data.title(), drawableWidth),
                            textX, textY, config.colors.lyricsHighlight, false);
                    textY += lineHeight;
                }
                if (cfg.showArtist && !data.artist().isBlank()) {
                    graphics.drawString(client.font, Ui.fit(client.font, data.artist(), drawableWidth),
                            textX, textY, config.colors.textSecondary, false);
                    textY += lineHeight + 2;
                }
                if (!data.synced()) {
                    graphics.drawString(client.font, "Unsynced lyrics", textX, textY,
                            config.colors.textSecondary, false);
                    textY += lineHeight;
                }

                List<LyricLine> lines = data.lines();
                int active = lyricsManager.currentIndex(snapshot.positionMs());
                int start = data.synced() && active >= 0 ? Math.max(0, active - cfg.maxLines / 2) : 0;
                int remainingLocal = (int) ((y + height - padding - textY) / fontScale);
                int available = Math.max(0, remainingLocal / Math.max(1, lineHeight));
                int count = Math.min(Math.min(cfg.maxLines, available), Math.max(0, lines.size() - start));

                for (int i = 0; i < count; i++) {
                    LyricLine line = lines.get(start + i);
                    int color = data.synced() && start + i == active
                            ? config.colors.lyricsHighlight : config.colors.lyricsText;
                    String text = Ui.fit(client.font, line.text(), drawableWidth);
                    if ("CENTER".equals(cfg.alignment)) {
                        graphics.drawCenteredString(client.font, text, textX + drawableWidth / 2, textY, color);
                    } else {
                        graphics.drawString(client.font, text, textX, textY, color, false);
                    }
                    textY += lineHeight;
                }
            } finally {
                graphics.pose().popMatrix();
            }
        } finally {
            graphics.pose().popMatrix();
        }
    }

    private static void renderStatus(GuiGraphics graphics, Minecraft client, String status,
                                     int x, int y, int width, int height, ModConfig.Lyrics cfg,
                                     ModConfig config) {
        String text = Ui.fit(client.font, status.isBlank() ? "Lyrics unavailable" : status,
                Math.max(1, width - cfg.padding * 2));
        int textY = clamp(y + height / 2 - client.font.lineHeight / 2,
                y + cfg.padding, y + Math.max(cfg.padding, height - client.font.lineHeight - cfg.padding));
        if ("CENTER".equals(cfg.alignment)) {
            graphics.drawCenteredString(client.font, text, x + width / 2, textY, config.colors.textSecondary);
        } else {
            graphics.drawString(client.font, text, x + cfg.padding, textY, config.colors.textSecondary, false);
        }
    }

    private static void scaleAround(GuiGraphics graphics, int x, int y, float scale) {
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        graphics.pose().translate(-x, -y);
    }

    private static int positionX(String position, int guiWidth, int width, int manualX) {
        return switch (position) {
            case "TOP_LEFT", "CENTER_LEFT", "BOTTOM_LEFT" -> 8;
            case "TOP_RIGHT", "CENTER_RIGHT", "BOTTOM_RIGHT" -> guiWidth - width - 8;
            case "TOP_CENTER", "CENTER", "BOTTOM_CENTER" -> (guiWidth - width) / 2;
            default -> manualX;
        };
    }

    private static int positionY(String position, int guiHeight, int height, int manualY) {
        return switch (position) {
            case "TOP_LEFT", "TOP_CENTER", "TOP_RIGHT" -> 8;
            case "CENTER_LEFT", "CENTER", "CENTER_RIGHT" -> (guiHeight - height) / 2;
            case "BOTTOM_LEFT", "BOTTOM_CENTER", "BOTTOM_RIGHT" -> guiHeight - height - 8;
            default -> manualY;
        };
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(Math.max(min, max), value));
    }
}
