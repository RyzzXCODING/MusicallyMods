package cc.alpoyy.musicallymods.hud;

import cc.alpoyy.musicallymods.config.MusicallyModsConfig;
import cc.alpoyy.musicallymods.provider.MusicProvider;
import cc.alpoyy.musicallymods.provider.ProviderRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Optional;

public class NowPlayingHud {
	public static void render(GuiGraphics gfx) {
		MusicallyModsConfig cfg = MusicallyModsConfig.get();
		Optional<MusicProvider> provOpt = ProviderRegistry.getActiveProvider();
		if (provOpt.isEmpty()) return;
		MusicProvider prov = provOpt.get();
		Optional<MusicProvider.TrackInfo> trackOpt = prov.getCurrentTrack();
		MusicProvider.PlaybackState state = prov.getPlaybackState();

		if (cfg.hudHideWhenNoTrack && trackOpt.isEmpty()) return;
		if (cfg.hudHideWhenPaused && !state.playing()) return;

		Minecraft mc = Minecraft.getInstance();
		int x = cfg.hudX;
		int y = cfg.hudY;
		int w = 160;
		int h = 40;

		gfx.fill(x, y, x + w, y + h, 0xAA171717);
		gfx.drawString(mc.font, "MusicallyMods", x + 4, y + 4, 0xFFFFFFFF, false);
		if (trackOpt.isPresent()) {
			MusicProvider.TrackInfo t = trackOpt.get();
			String line = (t.title() != null ? t.title() : "Unknown") + " - " + (t.artist() != null ? t.artist() : "Unknown");
			if (line.length() > 28) line = line.substring(0, 25) + "...";
			gfx.drawString(mc.font, line, x + 4, y + 16, 0xFFCCCCCC, false);
		} else {
			gfx.drawString(mc.font, (state.playing() ? "Playing" : "Paused/Idle"), x + 4, y + 16, 0xFFAAAAAA, false);
		}
	}
}
