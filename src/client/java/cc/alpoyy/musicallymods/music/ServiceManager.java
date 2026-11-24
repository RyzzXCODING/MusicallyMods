package cc.alpoyy.musicallymods.music;

import cc.alpoyy.musicallymods.audio.StreamResolvers;
import cc.alpoyy.musicallymods.auth.PlatformConnection;
import cc.alpoyy.musicallymods.config.ModConfig;
import cc.alpoyy.musicallymods.music.impl.CustomStreamService;
import cc.alpoyy.musicallymods.music.impl.DeezerService;
import cc.alpoyy.musicallymods.music.impl.RadioBrowserService;
import cc.alpoyy.musicallymods.music.impl.SoundCloudService;
import cc.alpoyy.musicallymods.music.impl.SpotifyService;
import cc.alpoyy.musicallymods.music.impl.YtDlpProcess;
import cc.alpoyy.musicallymods.music.impl.YtDlpService;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Registry of implemented integrations plus explicit, specific statuses for unavailable services. */
public final class ServiceManager {
    private static final YtDlpService YT_DLP = new YtDlpService();
    private static final SpotifyService SPOTIFY = new SpotifyService();
    private static final List<MusicService> SERVICES = List.of(
            new DeezerService(),
            new RadioBrowserService(),
            new CustomStreamService(),
            YT_DLP,
            SPOTIFY,
            new SoundCloudService());

    static {
        StreamResolvers.register("youtubemusic", YT_DLP);
    }

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

    public static void shutdown() {
        PlatformConnection.shutdown();
        YtDlpProcess.shutdown();
    }
}
