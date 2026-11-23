package cc.alpoyy.musicallymods.client;

import cc.alpoyy.musicallymods.MusicallyMods;
import cc.alpoyy.musicallymods.config.MusicallyModsConfig;
import cc.alpoyy.musicallymods.gui.screen.MusicallyMainScreen;
import cc.alpoyy.musicallymods.hud.NowPlayingHud;
import cc.alpoyy.musicallymods.provider.ProviderRegistry;
import cc.alpoyy.musicallymods.provider.spotify.SpotifyProvider;
import cc.alpoyy.musicallymods.provider.youtubemusic.YouTubeMusicProvider;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class MusicallyModsClient implements ClientModInitializer {
	private static KeyMapping openKey;
	private static KeyMapping playPauseKey;
	private static KeyMapping nextKey;
	private static KeyMapping prevKey;
	private static KeyMapping hudToggleKey;

	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
			Identifier.fromNamespaceAndPath(MusicallyMods.MOD_ID, "main"));

	private static boolean hudVisibleOverride = true;

	@Override
	public void onInitializeClient() {
		MusicallyMods.LOGGER.info("MusicallyMods client initializing...");

		MusicallyModsConfig.load();

		ProviderRegistry.register(new SpotifyProvider());
		ProviderRegistry.register(new YouTubeMusicProvider());

		registerKeybindings();
		registerHud();

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openKey != null && openKey.consumeClick()) {
				if (client.screen == null) {
					client.setScreen(new MusicallyMainScreen());
				} else if (client.screen instanceof MusicallyMainScreen) {
					client.setScreen(null);
				}
			}
			while (playPauseKey != null && playPauseKey.consumeClick()) {
				ProviderRegistry.getActiveProvider().ifPresent(p -> {
					try {
						p.togglePlayPause();
					} catch (Exception e) {
						MusicallyMods.LOGGER.warn("Play/pause failed: {}", e.getMessage());
					}
				});
			}
			while (nextKey != null && nextKey.consumeClick()) {
				ProviderRegistry.getActiveProvider().ifPresent(p -> {
					try {
						p.next();
					} catch (Exception e) {
						MusicallyMods.LOGGER.warn("Next failed: {}", e.getMessage());
					}
				});
			}
			while (prevKey != null && prevKey.consumeClick()) {
				ProviderRegistry.getActiveProvider().ifPresent(p -> {
					try {
						p.previous();
					} catch (Exception e) {
						MusicallyMods.LOGGER.warn("Previous failed: {}", e.getMessage());
					}
				});
			}
			while (hudToggleKey != null && hudToggleKey.consumeClick()) {
				hudVisibleOverride = !hudVisibleOverride;
				MusicallyModsConfig.get().hudEnabled = hudVisibleOverride;
				MusicallyModsConfig.save();
			}
		});
	}

	private void registerKeybindings() {
		openKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.musicallymods.open",
				GLFW.GLFW_KEY_M,
				CATEGORY
		));
		playPauseKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.musicallymods.playpause",
				GLFW.GLFW_KEY_UNKNOWN,
				CATEGORY
		));
		nextKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.musicallymods.next",
				GLFW.GLFW_KEY_UNKNOWN,
				CATEGORY
		));
		prevKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.musicallymods.prev",
				GLFW.GLFW_KEY_UNKNOWN,
				CATEGORY
		));
		hudToggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.musicallymods.hudtoggle",
				GLFW.GLFW_KEY_UNKNOWN,
				CATEGORY
		));
	}

	private void registerHud() {
		HudRenderCallback.EVENT.register((guiGraphics, delta) -> {
			if (!MusicallyModsConfig.get().hudEnabled || !hudVisibleOverride) return;
			NowPlayingHud.render(guiGraphics);
		});
	}

	public static boolean isHudVisibleOverride() {
		return hudVisibleOverride;
	}
}
