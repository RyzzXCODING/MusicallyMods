package cc.alpoyy.musicallymods.provider;

import cc.alpoyy.musicallymods.config.MusicallyModsConfig;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class ProviderRegistry {
	private static final Map<String, MusicProvider> PROVIDERS = new LinkedHashMap<>();

	public static void register(MusicProvider provider) {
		PROVIDERS.put(provider.getId(), provider);
	}

	public static Optional<MusicProvider> get(String id) {
		return Optional.ofNullable(PROVIDERS.get(id));
	}

	public static Collection<MusicProvider> all() {
		return PROVIDERS.values();
	}

	public static Optional<MusicProvider> getActiveProvider() {
		String id = MusicallyModsConfig.get().defaultProviderId;
		if (id == null || id.isBlank()) {
			return PROVIDERS.values().stream().findFirst();
		}
		return get(id);
	}
}
