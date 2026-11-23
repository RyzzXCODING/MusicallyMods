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

        LyricsData data = LyricsManager.current();
        if (data.isEmpty()) return;
        Track track = LyricsManager.currentTrack();
        PlaybackSnapshot snapshot = AudioManager.get().snapshot();

        int width = cfg.width;
        int height = cfg.height;
        int x = positionX(cfg.position, graphics.guiWidth(), width, cfg.manualX);
        int y = positionY(cfg.position, graphics.guiHeight(), height, cfg.manualY);
        int padding = cfg.padding;
        int alphaBg = Ui.withAlpha(config.colors.lyricsBg, cfg.transparency);

        graphics.pose().pushMatrix();
        if (cfg.scale != 1f) scaleAround(graphics, x, y, cfg.scale);
        graphics.fill(x, y, x + width, y + height, alphaBg);
        graphics.renderOutline(x, y, width, height, config.colors.accent);

        int textX = x + padding;
        int textY = y + padding;
        int textWidth = width - padding * 2;
        float fontScale = Math.max(0.5f, cfg.fontSize);
        if (cfg.showArtwork && track != null && !track.artworkUrl().isBlank()) {
            ArtworkCache.request(track);
            int size = Math.min(48, height - padding * 2);
            if (ArtworkCache.has(track)) {
                ArtworkCache.render(graphics, track, textX, textY, size, size);
            } else {
                graphics.fill(textX, textY, textX + size, textY + size, config.colors.controls);
            }
            textX += size + padding;
            textWidth -= size + padding;
        }

        if (textWidth <= 0 || textX >= x + width || textY >= y + height) {
            graphics.pose().popMatrix();
            return;
        }

        graphics.pose().pushMatrix();
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
        int active = LyricsManager.get().currentIndex(snapshot.positionMs());
        int start = 0;
        if (data.synced() && active >= 0) start = Math.max(0, active - cfg.maxLines / 2);
        int remainingLocal = (int) ((y + height - padding - textY) / fontScale);
        int available = Math.max(0, remainingLocal / Math.max(1, lineHeight));
        int count = Math.min(Math.min(cfg.maxLines, available), Math.max(0, lines.size() - start));

        for (int i = 0; i < count; i++) {
            LyricLine line = lines.get(start + i);
            int color = data.synced() && start + i == active
                    ? config.colors.lyricsHighlight : config.colors.lyricsText;
            String text = Ui.fit(client.font, line.text(), drawableWidth);
            if (cfg.alignment.equals("CENTER")) {
                graphics.drawCenteredString(client.font, text, textX + drawableWidth / 2, textY, color);
            } else {
                graphics.drawString(client.font, text, textX, textY, color, false);
            }
            textY += lineHeight;
        }
        graphics.pose().popMatrix();
        graphics.pose().popMatrix();
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
}
