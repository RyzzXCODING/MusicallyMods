package cc.alpoyy.musicallymods.gui.screen;

import cc.alpoyy.musicallymods.MusicallyMods;
import cc.alpoyy.musicallymods.gui.theme.Theme;
import cc.alpoyy.musicallymods.provider.MusicProvider;
import cc.alpoyy.musicallymods.provider.ProviderRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class MusicallyMainScreen extends Screen {
	private enum Tab { HOME, SEARCH, LIBRARY, PLAYLISTS, ACCOUNTS, SETTINGS }
	private Tab currentTab = Tab.HOME;

	public MusicallyMainScreen() {
		super(Component.translatable("screen.musicallymods.title"));
	}

	@Override
	protected void init() {
		int y = 10;
		int bw = 80;
		int gap = 4;
		int x = 10;
		addRenderableWidget(Button.builder(Component.translatable("screen.musicallymods.home"), b -> currentTab = Tab.HOME).bounds(x, y, bw, 18).build());
		x += bw + gap;
		addRenderableWidget(Button.builder(Component.translatable("screen.musicallymods.search"), b -> currentTab = Tab.SEARCH).bounds(x, y, bw, 18).build());
		x += bw + gap;
		addRenderableWidget(Button.builder(Component.translatable("screen.musicallymods.library"), b -> currentTab = Tab.LIBRARY).bounds(x, y, bw, 18).build());
		x += bw + gap;
		addRenderableWidget(Button.builder(Component.translatable("screen.musicallymods.playlists"), b -> currentTab = Tab.PLAYLISTS).bounds(x, y, bw, 18).build());
		x += bw + gap;
		addRenderableWidget(Button.builder(Component.translatable("screen.musicallymods.accounts"), b -> currentTab = Tab.ACCOUNTS).bounds(x, y, bw, 18).build());
		x += bw + gap;
		addRenderableWidget(Button.builder(Component.translatable("screen.musicallymods.settings"), b -> currentTab = Tab.SETTINGS).bounds(x, y, bw, 18).build());
	}

	@Override
	public void render(GuiGraphics gfx, int mouseX, int mouseY, float delta) {
		this.renderBackground(gfx, mouseX, mouseY, delta);
		gfx.fill(0, 0, this.width, this.height, Theme.BG);
		// Icon drawing skipped in this build step to avoid API breakage; icon is present in assets.
		super.render(gfx, mouseX, mouseY, delta);
		gfx.fill(8, 36, this.width - 8, this.height - 8, Theme.PANEL);
		gfx.drawString(this.font, "MusicallyMods - " + currentTab.name(), 14, 42, Theme.TEXT, false);

		int yy = 60;
		switch (currentTab) {
			case HOME -> {
				gfx.drawString(this.font, "Connected providers and quick actions:", 14, yy, Theme.TEXT_SECONDARY, false);
				yy += 14;
				for (MusicProvider p : ProviderRegistry.all()) {
					String status = p.isConnected() ? "Connected" : "Not connected";
					gfx.drawString(this.font, "- " + p.getDisplayName() + ": " + status, 18, yy, Theme.TEXT, false);
					yy += 12;
				}
				yy += 6;
				gfx.drawString(this.font, "Default provider: " + ProviderRegistry.getActiveProvider().map(MusicProvider::getDisplayName).orElse("None"), 18, yy, Theme.TEXT_SECONDARY, false);
			}
			case SEARCH -> gfx.drawString(this.font, "Search scaffold: wire provider search async in next iteration.", 14, yy, Theme.TEXT_SECONDARY, false);
			case LIBRARY -> gfx.drawString(this.font, "Library scaffold: authenticate a provider to load real data.", 14, yy, Theme.TEXT_SECONDARY, false);
			case PLAYLISTS -> gfx.drawString(this.font, "Playlists scaffold: authenticate a provider to load real data.", 14, yy, Theme.TEXT_SECONDARY, false);
			case ACCOUNTS -> {
				gfx.drawString(this.font, "Accounts:", 14, yy, Theme.TEXT_SECONDARY, false);
				yy += 14;
				for (MusicProvider p : ProviderRegistry.all()) {
					gfx.drawString(this.font, p.getDisplayName() + " - " + (p.isConnected() ? "Connected" : "Disconnected"), 18, yy, Theme.TEXT, false);
					yy += 12;
					String desc = p.getCapabilitiesDescription();
					if (desc.length() > 60) desc = desc.substring(0, 57) + "...";
					gfx.drawString(this.font, "  " + desc, 18, yy, Theme.TEXT_SECONDARY, false);
					yy += 14;
				}
			}
			case SETTINGS -> {
				gfx.drawString(this.font, "Settings: config/musicallymods.json", 14, yy, Theme.TEXT_SECONDARY, false);
				yy += 12;
				gfx.drawString(this.font, "Rebind keys in Minecraft Controls (key: Open MusicallyMods = M).", 14, yy, Theme.TEXT_SECONDARY, false);
			}
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
