package cc.alpoyy.musicallymods.music;

import cc.alpoyy.musicallymods.config.ModConfig;
import cc.alpoyy.musicallymods.music.impl.CustomStreamService;
import cc.alpoyy.musicallymods.music.impl.DeezerService;
import cc.alpoyy.musicallymods.music.impl.RadioBrowserService;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Registry of real integrations plus explicit entries for services we do not integrate. */
public final class ServiceManager {
    private static final List<MusicService> SERVICES = List.of(
            new DeezerService(),
            new RadioBrowserService(),
            new CustomStreamService(),
            new UnavailableService("spotify", "Spotify (not integrated)",
                    "Spotify Web API needs your own OAuth app credentials; playback control needs an active Spotify Connect device and may require Premium. No credentials are bundled."),
            new UnavailableService("youtubemusic", "YouTube Music (not integrated)",
                    "YouTube Music has no official public third-party playback API; unofficial clients risk Terms of Service violations, so it is not integrated."),
            new UnavailableService("soundcloud", "SoundCloud (not integrated)",
                    "SoundCloud's public API now requires developer authorization; no user credentials or tokens are bundled."));

    private ServiceManager() {
    }

    public static List<MusicService> all() {
        return SERVICES;
    }

    public static Optional<MusicService> get(String id) {
        return SERVICES.stream().filter(s -> s.id().equals(id)).findFirst();
    }

    public static MusicService active() {
        String id = ModConfig.get().selectedService;
        return get(id).orElse(SERVICES.get(0));
    }

    public static void select(String id) {
        if (get(id).isPresent()) {
            ModConfig.get().selectedService = id;
            ModConfig.markDirty();
        }
    }

    public static CompletableFuture<ServiceResult<List<Track>>> search(String query) {
        if (query == null || query.isBlank()) {
            return CompletableFuture.completedFuture(ServiceResult.failure("Enter a search query first."));
        }
        return active().search(query.trim());
    }
}
