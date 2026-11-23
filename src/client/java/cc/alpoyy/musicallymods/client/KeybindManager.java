package cc.alpoyy.musicallymods.client;

import cc.alpoyy.musicallymods.MusicallyMods;
import cc.alpoyy.musicallymods.audio.AudioManager;
import cc.alpoyy.musicallymods.config.ModConfig;
import cc.alpoyy.musicallymods.gui.MusicPlayerScreen;
import cc.alpoyy.musicallymods.gui.MusicSettingsScreen;
import cc.alpoyy.musicallymods.lyrics.LyricsManager;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/** Registers the mod's configurable keybinds and handles their client-tick actions. */
public final class KeybindManager {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(MusicallyMods.MOD_ID, "main"));

    private static KeyMapping open;
    private static KeyMapping lyrics;
    private static KeyMapping playPause;
    private static KeyMapping next;
    private static KeyMapping previous;

    private KeybindManager() {
    }

    public static void register() {
        ModConfig.Keybinds keys = ModConfig.get().keybinds;
        open = bind("key.musicallymods.open", keys.open, GLFW.GLFW_KEY_M);
        lyrics = bind("key.musicallymods.lyrics", keys.lyrics, GLFW.GLFW_KEY_K);
        playPause = bind("key.musicallymods.playpause", keys.playPause, GLFW.GLFW_KEY_P);
        next = bind("key.musicallymods.next", keys.next, GLFW.GLFW_KEY_N);
        previous = bind("key.musicallymods.prev", keys.previous, GLFW.GLFW_KEY_B);
    }

    public static void handle(Minecraft client) {
        while (open != null && open.consumeClick()) {
            if (client.screen == null) {
                client.setScreen(new MusicPlayerScreen());
            } else if (client.screen instanceof MusicPlayerScreen || client.screen instanceof MusicSettingsScreen) {
                client.setScreen(null);
            }
        }
        if (client.screen != null) return;

        while (lyrics != null && lyrics.consumeClick()) {
            LyricsManager.get().toggleEnabled();
        }
        while (playPause != null && playPause.consumeClick()) {
            AudioManager.get().togglePlayPause();
        }
        while (next != null && next.consumeClick()) {
            AudioManager.get().next();
        }
        while (previous != null && previous.consumeClick()) {
            AudioManager.get().previous();
        }
    }

    private static KeyMapping bind(String translationKey, String configured, int fallback) {
        int code = fallback;
        try {
            InputConstants.Key key = InputConstants.getKey(configured);
            if (key != null && key.getValue() >= 0) code = key.getValue();
        } catch (RuntimeException e) {
            MusicallyMods.LOGGER.warn("Invalid keybind '{}' for {}; using default.", configured, translationKey);
        }
        return KeyBindingHelper.registerKeyBinding(new KeyMapping(translationKey, code, CATEGORY));
    }
}
