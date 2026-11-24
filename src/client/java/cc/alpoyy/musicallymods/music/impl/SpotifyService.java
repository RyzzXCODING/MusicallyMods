package cc.alpoyy.musicallymods.music.impl;

import cc.alpoyy.musicallymods.auth.PlatformConnection;
import cc.alpoyy.musicallymods.auth.SpotifyConnection;
import cc.alpoyy.musicallymods.music.MusicService;
import cc.alpoyy.musicallymods.music.ServiceCapabilities;
import cc.alpoyy.musicallymods.music.ServiceResult;
import cc.alpoyy.musicallymods.music.ServiceStatus;
import cc.alpoyy.musicallymods.music.Track;
import cc.alpoyy.musicallymods.util.Errors;
import cc.alpoyy.musicallymods.util.JsonUtil;
import cc.alpoyy.musicallymods.util.NetworkManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Spotify catalog metadata through the official Web API. Raw audio is never exposed to Minecraft;
 * playback is delegated to an active, unrestricted Spotify Connect device chosen in the platform tab.
 */
public final class SpotifyService implements MusicService {
    private static final String NOTE =
            "Official Web API metadata only. Raw audio is not exposed; use Spotify Connect for playback. "
                    + "Connect control requires Premium and an unrestricted active device.";

    @Override
    public String id() {
        return "spotify";
    }

    @Override
    public String displayName() {
        return "Spotify (official Web API)";
    }

    @Override
    public ServiceCapabilities capabilities() {
        return new ServiceCapabilities(true, true, true, false, false, false, false, false, NOTE);
    }

    @Override
    public String statusText() {
        return status().line();
    }

    @Override
    public ServiceStatus status() {
        PlatformConnection.View view = PlatformConnection.spotify().view();
        boolean connected = view.state() == PlatformConnection.State.CONNECTED;
        return new ServiceStatus(
                connected ? ServiceStatus.IntegrationLevel.METADATA_ONLY
                        : ServiceStatus.IntegrationLevel.NOT_INTEGRATED,
                ServiceStatus.PlaybackSupport.EXTERNAL_PLAYER_ONLY,
                ServiceStatus.AuthRequirement.USER_OAUTH,
                connected ? "Connected - Spotify Web API metadata available" : "Spotify - " + view.label(),
                view.detail().isBlank() ? NOTE : view.detail());
    }

    @Override
    public CompletableFuture<ServiceResult<List<Track>>> search(String query) {
        if (query == null || query.isBlank()) {
            return CompletableFuture.completedFuture(ServiceResult.failure("Enter a search query first."));
        }
        SpotifyConnection connection = PlatformConnection.spotify();
        if (connection.view().state() != PlatformConnection.State.CONNECTED) {
            return CompletableFuture.completedFuture(
                    ServiceResult.failure("Connect through the Spotify tab before searching: "
                            + connection.view().detail()));
        }
        return connection.accessToken().thenCompose(token -> {
            if (token.isBlank()) {
                return CompletableFuture.completedFuture(
                        ServiceResult.failure("Spotify session is not available. Reconnect in the Spotify tab."));
            }
            String endpoint = "https://api.spotify.com/v1/search?q="
                    + URLEncoder.encode(query.trim(), StandardCharsets.UTF_8)
                    + "&type=track&limit=10";
            return NetworkManager.getString(endpoint, Map.of("Authorization", "Bearer " + token))
                    .handle((body, throwable) -> {
                        if (throwable != null) {
                            return ServiceResult.<List<Track>>failure(
                                    "Spotify search failed: " + Errors.describe(throwable));
                        }
                        try {
                            JsonObject root = JsonUtil.object(JsonParser.parseString(body));
                            JsonObject tracks = root == null ? null : JsonUtil.object(root.get("tracks"));
                            JsonArray items = tracks == null ? null : tracks.getAsJsonArray("items");
                            List<Track> results = new ArrayList<>();
                            if (items != null) {
                                for (JsonElement element : items) {
                                    Track track = toTrack(JsonUtil.object(element));
                                    if (track != null) results.add(track);
                                }
                            }
                            if (results.isEmpty()) {
                                return ServiceResult.<List<Track>>failure("No Spotify tracks were returned.");
                            }
                            return ServiceResult.success(List.copyOf(results));
                        } catch (RuntimeException e) {
                            return ServiceResult.<List<Track>>failure(
                                    "Spotify returned an unreadable response: " + Errors.describe(e));
                        }
                    });
        });
    }

    private static Track toTrack(JsonObject item) {
        if (item == null) return null;
        String id = JsonUtil.string(item, "id");
        String title = JsonUtil.string(item, "name");
        if (id == null && title == null) return null;
        String artist = firstArtist(item.getAsJsonArray("artists"));
        JsonObject album = JsonUtil.object(item.get("album"));
        String artwork = firstImage(album == null ? null : album.getAsJsonArray("images"));
        JsonObject external = JsonUtil.object(item.get("external_urls"));
        String url = JsonUtil.string(external, "spotify");
        String uri = JsonUtil.string(item, "uri");
        String albumName = album == null ? null : JsonUtil.string(album, "name");
        String note = NOTE + (JsonUtil.string(item, "preview_url") == null
                ? "" : " A legacy 30-second preview may exist, but it is not used here.");
        return new Track(id == null ? "" : id, title == null ? id : title,
                artist == null ? "Unknown artist" : artist,
                albumName == null ? "" : albumName,
                artwork == null ? "" : artwork,
                url == null ? (uri == null ? "" : uri) : url,
                JsonUtil.number(item, "duration_ms", 0L),
                "spotify", note, false);
    }

    private static String firstArtist(JsonArray artists) {
        if (artists == null || artists.isEmpty()) return null;
        JsonObject first = JsonUtil.object(artists.get(0));
        return first == null ? null : JsonUtil.string(first, "name");
    }

    private static String firstImage(JsonArray images) {
        if (images == null || images.isEmpty()) return null;
        for (int i = images.size() - 1; i >= 0; i--) {
            JsonObject image = JsonUtil.object(images.get(i));
            String url = JsonUtil.string(image, "url");
            if (url != null && url.startsWith("https://")) return url;
        }
        return null;
    }
}
