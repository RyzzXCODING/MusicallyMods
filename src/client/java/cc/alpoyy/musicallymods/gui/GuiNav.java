package cc.alpoyy.musicallymods.gui;

import cc.alpoyy.musicallymods.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;

/** Shared six-tab navigation used by every main MusicallyMods screen. */
public final class GuiNav {
    public enum Tab {
        HOME("Home", "Home"),
        YOUTUBE_MUSIC("YouTube Music", "YT Music"),
        SPOTIFY("Spotify", "Spotify"),
        SOUND_CLOUD("SoundCloud", "SoundCloud"),
        LYRICS("Lyrics", "Lyrics"),
        SETTINGS("Settings", "Settings");

        private final String fullLabel;
        private final String shortLabel;

        Tab(String fullLabel, String shortLabel) {
            this.fullLabel = fullLabel;
            this.shortLabel = shortLabel;
        }
    }

    private final List<Ui.Rect> rects = new ArrayList<>();

    /** Draws the bar and records clipped click targets; returns its bottom edge. */
    public int render(GuiGraphics graphics, Font font, int x, int y, int width, int height,
                      int screenWidth, int screenHeight, Tab active, ModConfig.Colors c,
                      int mouseX, int mouseY) {
        rects.clear();
        if (height <= 0 || width <= 0) return y;
        Ui.Rect bounds = new Ui.Rect(0, 0, screenWidth, screenHeight);
        Tab[] tabs = Tab.values();
        int gap = width < 560 ? 2 : 4;
        int tabWidth = Math.max(16, (width - gap * (tabs.length - 1)) / tabs.length);
        for (int i = 0; i < tabs.length; i++) {
            Ui.Rect rect = new Ui.Rect(x + i * (tabWidth + gap), y, tabWidth, height).intersection(bounds);
            rects.add(rect);
            boolean selected = tabs[i] == active;
            String label = width < 700 ? tabs[i].shortLabel : tabs[i].fullLabel;
            Ui.button(graphics, font, rect, Ui.fit(font, label, rect.width() - 4),
                    selected ? c.controls : c.mainBg, c.accent, c.textPrimary,
                    true, rect.contains(mouseX, mouseY));
        }
        return y + height;
    }

    /** Returns the tab under the pointer, or null when the click is outside the bar. */
    public Tab clicked(double mouseX, double mouseY) {
        for (int i = 0; i < rects.size(); i++) {
            if (rects.get(i).contains(mouseX, mouseY)) return Tab.values()[i];
        }
        return null;
    }

    /** Opens a tab without creating a MusicallyMods account, backend or browser popup. */
    public static void open(Minecraft minecraft, Tab tab, Screen parent) {
        if (minecraft == null || tab == null) return;
        Screen next = switch (tab) {
            case HOME -> new MusicPlayerScreen();
            case YOUTUBE_MUSIC -> new PlatformScreen(parent, PlatformScreen.Provider.YOUTUBE_MUSIC);
            case SPOTIFY -> new PlatformScreen(parent, PlatformScreen.Provider.SPOTIFY);
            case SOUND_CLOUD -> new PlatformScreen(parent, PlatformScreen.Provider.SOUND_CLOUD);
            case LYRICS -> new MusicSettingsScreen(parent, 1);
            case SETTINGS -> new MusicSettingsScreen(parent, 0);
        };
        minecraft.setScreen(next);
    }
}
