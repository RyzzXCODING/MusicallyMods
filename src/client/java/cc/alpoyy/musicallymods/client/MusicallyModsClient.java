package cc.alpoyy.musicallymods.client;

import cc.alpoyy.musicallymods.MusicallyMods;
import cc.alpoyy.musicallymods.audio.AudioManager;
import cc.alpoyy.musicallymods.config.ModConfig;
import cc.alpoyy.musicallymods.hud.LyricsHud;
import cc.alpoyy.musicallymods.music.ServiceManager;
import cc.alpoyy.musicallymods.util.NetworkManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;

public final class MusicallyModsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ModConfig.load();
        KeybindManager.register();
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(MusicallyMods.MOD_ID, "lyrics"), new LyricsHud());

        ClientTickEvents.END_CLIENT_TICK.register(KeybindManager::handle);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            ModConfig.saveIfDirty();
            AudioManager.get().shutdown();
            ServiceManager.shutdown();
            NetworkManager.shutdown();
        });

        MusicallyMods.LOGGER.info(
                "MusicallyMods client ready. Integrations: Deezer previews, Radio Browser stations, direct streams, "
                        + "optional opt-in yt-dlp bridge, LRCLIB lyrics. Spotify and SoundCloud remain explicitly unavailable.");
    }
}
