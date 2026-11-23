package cc.alpoyy.musicallymods.config;

import cc.alpoyy.musicallymods.MusicallyMods;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class MusicallyModsConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static MusicallyModsConfig INSTANCE = new MusicallyModsConfig();

	public String defaultProviderId = "spotify";
	public boolean hudEnabled = true;
	public int hudX = 8;
	public int hudY = 8;
	public float hudScale = 1.0f;
	public boolean hudHideWhenPaused = false;
	public boolean hudHideWhenNoTrack = true;
	public boolean notificationsEnabled = true;
	public int pollingIntervalSeconds = 5;
	public float uiScale = 1.0f;
	public float backgroundOpacity = 0.9f;
	public boolean debugLogging = false;

	public static MusicallyModsConfig get() {
		return INSTANCE;
	}

	public static Path getConfigPath() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("musicallymods.json");
	}

	public static void load() {
		Path p = getConfigPath();
		if (Files.exists(p)) {
			try {
				String s = Files.readString(p);
				INSTANCE = GSON.fromJson(s, MusicallyModsConfig.class);
				if (INSTANCE == null) INSTANCE = new MusicallyModsConfig();
			} catch (Exception e) {
				MusicallyMods.LOGGER.warn("Failed to load config, using defaults: {}", e.getMessage());
				INSTANCE = new MusicallyModsConfig();
			}
		} else {
			save();
		}
	}

	public static void save() {
		try {
			Files.createDirectories(getConfigPath().getParent());
			Files.writeString(getConfigPath(), GSON.toJson(INSTANCE));
		} catch (IOException e) {
			MusicallyMods.LOGGER.error("Failed to save config: {}", e.getMessage());
		}
	}
}
