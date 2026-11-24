package cc.alpoyy.musicallymods.auth;

import java.util.concurrent.CompletableFuture;

/** Registry and shared state vocabulary for the three independent platform connections. */
public final class PlatformConnection {
    public enum State {
        NOT_CONNECTED, CONNECTING, WAITING_FOR_AUTH, CONNECTED, RECONNECTING,
        TOKEN_EXPIRED, AUTHORIZATION_FAILED, DISCONNECTED, PLAYBACK_UNAVAILABLE, INTEGRATION_ERROR
    }

    public record View(State state, String account, String error, String detail,
                       boolean hasSession, boolean playbackAvailable) {
        public View {
            account = account == null ? "" : account;
            error = error == null ? "" : error;
            detail = detail == null ? "" : detail;
        }

        public String label() {
            return switch (state) {
                case NOT_CONNECTED -> "Not Connected";
                case CONNECTING -> "Connecting";
                case WAITING_FOR_AUTH -> "Waiting for Authentication";
                case CONNECTED -> "Connected";
                case RECONNECTING -> "Reconnecting";
                case TOKEN_EXPIRED -> "Session Expired - Reconnect Required";
                case AUTHORIZATION_FAILED -> "Authorization Failed";
                case DISCONNECTED -> "Disconnected";
                case PLAYBACK_UNAVAILABLE -> "Playback Unavailable";
                case INTEGRATION_ERROR -> "Integration Error";
            };
        }
    }

    private static final SpotifyConnection SPOTIFY = new SpotifyConnection();
    private static final SoundCloudConnection SOUND_CLOUD = new SoundCloudConnection();
    private static final YouTubeMusicConnection YOUTUBE_MUSIC = new YouTubeMusicConnection();

    private PlatformConnection() {
    }

    public static SpotifyConnection spotify() {
        return SPOTIFY;
    }

    public static SoundCloudConnection soundCloud() {
        return SOUND_CLOUD;
    }

    public static YouTubeMusicConnection youTubeMusic() {
        return YOUTUBE_MUSIC;
    }

    /** Silent startup reconnect; each provider is isolated so one failure cannot affect the others. */
    public static CompletableFuture<Void> reconnectAll() {
        CompletableFuture<Void> spotify = isolated(SPOTIFY.reconnect(), "Spotify");
        CompletableFuture<Void> soundCloud = isolated(SOUND_CLOUD.reconnect(), "SoundCloud");
        CompletableFuture<Void> youtube = isolated(YOUTUBE_MUSIC.reconnect(), "YouTube Music");
        return CompletableFuture.allOf(spotify, soundCloud, youtube);
    }

    public static void shutdown() {
        SPOTIFY.cancel();
        SOUND_CLOUD.cancel();
        OAuthClient.shutdown();
    }

    private static CompletableFuture<Void> isolated(CompletableFuture<Void> future, String provider) {
        try {
            return future.exceptionally(throwable -> null);
        } catch (RuntimeException e) {
            return CompletableFuture.completedFuture(null);
        }
    }
}
