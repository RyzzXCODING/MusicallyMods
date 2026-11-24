package cc.alpoyy.musicallymods.music;

import cc.alpoyy.musicallymods.audio.StreamResolvers;
import cc.alpoyy.musicallymods.config.ModConfig;
import cc.alpoyy.musicallymods.music.impl.CustomStreamService;
import cc.alpoyy.musicallymods.music.impl.DeezerService;
import cc.alpoyy.musicallymods.music.impl.RadioBrowserService;
import cc.alpoyy.musicallymods.music.impl.YtDlpProcess;
import cc.alpoyy.musicallymods.music.impl.YtDlpService;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Registry of implemented integrations plus explicit, specific statuses for unavailable services. */
public final class ServiceManager {
    private static final YtDlpService YT_DLP = new YtDlpService();
    private static final List<MusicService> SERVICES = List.of(
            new DeezerService(),
            new RadioBrowserService(),
            new CustomStreamService(),
            YT_DLP,
            spotifyStatus(),
            soundCloudStatus());

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
        YtDlpProcess.shutdown();
    }

    private static UnavailableService spotifyStatus() {
        return new UnavailableService("spotify", "Spotify (official API only)",
                new ServiceStatus(
                        ServiceStatus.IntegrationLevel.NOT_INTEGRATED,
                        ServiceStatus.PlaybackSupport.EXTERNAL_PLAYER_ONLY,
                        ServiceStatus.AuthRequirement.USER_OAUTH,
                        "Not integrated - official Web API requires user OAuth",
                        "The Spotify Web API can search metadata and remotely control an active Spotify "
                                + "Connect device, but it never exposes raw audio to Minecraft. Playback control is "
                                + "Premium-only and needs user-modify-playback-state plus an active, unrestricted device. "
                                + "A distributed client must use Authorization Code with PKCE and a user-registered "
                                + "Client ID; no client secret, token, cookie or DRM bypass is bundled. Free accounts "
                                + "and devices with is_restricted=true cannot be controlled through Web API playback."));
    }

    private static UnavailableService soundCloudStatus() {
        return new UnavailableService("soundcloud", "SoundCloud (not integrated)",
                new ServiceStatus(
                        ServiceStatus.IntegrationLevel.NOT_INTEGRATED,
                        ServiceStatus.PlaybackSupport.UNSUPPORTED,
                        ServiceStatus.AuthRequirement.DEVELOPER_APP,
                        "Not integrated - developer authorization required",
                        "SoundCloud's public API requires developer authorization/approval. This mod bundles no "
                                + "client ID, user token, cookie or Premium bypass, so no search or playback is offered."));
    }
}
