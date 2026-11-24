package cc.alpoyy.musicallymods.music.impl;

import cc.alpoyy.musicallymods.auth.PlatformConnection;
import cc.alpoyy.musicallymods.auth.SoundCloudConnection;
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
 * SoundCloud catalog metadata through the current official API. Only tracks whose official
 * {@code access} field is {@code playable} are listed. The mod's Java audio stack cannot decode
 * SoundCloud's HLS transcodes, so in-game playback is explicitly unavailable and no stream URL is
 * invented.
 */
public final class SoundCloudService implements MusicService {
    private static final String NOTE =
            "Official SoundCloud API metadata. Creditor and SoundCloud attribution are shown with every result. "
                    + "In-game audio is unavailable because the official stream URLs are HLS; open the track on "
                    + "SoundCloud to listen.";
    private static final String SEARCH = "https://api.soundcloud.com/tracks?q=%s&access=playable"
            + "&limit=10&linked_partitioning=true";

    @Override
    public String id() {
        return "soundcloud";
    }

    @Override
    public String displayName() {
        return "SoundCloud (official API)";
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
        PlatformConnection.View view = PlatformConnection.soundCloud().view();
        boolean configured = PlatformConnection.soundCloud().configured();
        boolean connected = view.state() == PlatformConnection.State.CONNECTED;
        return new ServiceStatus(
                connected ? ServiceStatus.IntegrationLevel.METADATA_ONLY
                        : configured ? ServiceStatus.IntegrationLevel.NOT_INTEGRATED
                        : ServiceStatus.IntegrationLevel.NOT_INTEGRATED,
                ServiceStatus.PlaybackSupport.UNSUPPORTED,
                ServiceStatus.AuthRequirement.DEVELOPER_APP,
                connected ? "Connected - official SoundCloud metadata available" : "SoundCloud - " + view.label(),
                view.detail().isBlank() ? NOTE : view.detail());
    }

    @Override
    public CompletableFuture<ServiceResult<List<Track>>> search(String query) {
        if (query == null || query.isBlank()) {
            return CompletableFuture.completedFuture(ServiceResult.failure("Enter a search query first."));
        }
        SoundCloudConnection connection = PlatformConnection.soundCloud();
        if (!connection.configured()) {
            return CompletableFuture.completedFuture(ServiceResult.failure(
                    "SoundCloud API access requires your own registered app credentials. See the SoundCloud tab."));
        }
        if (connection.view().state() != PlatformConnection.State.CONNECTED) {
            return CompletableFuture.completedFuture(ServiceResult.failure(
                    "Connect in the SoundCloud tab before searching: " + connection.view().detail()));
        }
        return connection.accessToken().thenCompose(token -> {
            if (token.isBlank()) {
                return CompletableFuture.completedFuture(ServiceResult.failure(
                        "The SoundCloud session is not available. Reconnect in the SoundCloud tab."));
            }
            String endpoint = String.format(SEARCH, URLEncoder.encode(query.trim(), StandardCharsets.UTF_8));
            return NetworkManager.getString(endpoint, Map.of("Authorization", "OAuth " + token))
                    .handle((body, throwable) -> {
                        if (throwable != null) {
                            String message = Errors.describe(throwable);
                            if (message.contains("HTTP 401")) {
                                connection.expire();
                                return ServiceResult.<List<Track>>failure("SoundCloud session expired; reconnect in the SoundCloud tab.");
                            }
                            if (message.contains("HTTP 429")) {
                                return ServiceResult.<List<Track>>failure("SoundCloud rate limit reached; wait before searching again.");
                            }
                            return ServiceResult.<List<Track>>failure("SoundCloud search failed: " + message);
                        }
                        try {
                            JsonObject root = JsonUtil.object(JsonParser.parseString(body));
                            JsonArray items = collection(root);
                            List<Track> results = new ArrayList<>();
                            if (items != null) {
                                for (JsonElement element : items) {
                                    Track track = toTrack(JsonUtil.object(element));
                                    if (track != null) results.add(track);
                                }
                            }
                            if (results.isEmpty()) {
                                return ServiceResult.<List<Track>>failure("No playable SoundCloud tracks were returned.");
                            }
                            return ServiceResult.success(List.copyOf(results));
                        } catch (RuntimeException e) {
                            return ServiceResult.<List<Track>>failure(
                                    "SoundCloud returned an unreadable response: " + Errors.describe(e));
                        }
                    });
        });
    }

    private static JsonArray collection(JsonObject root) {
        if (root == null) return null;
        JsonElement collection = root.get("collection");
        return collection != null && collection.isJsonArray() ? collection.getAsJsonArray() : null;
    }

    private static Track toTrack(JsonObject item) {
        if (item == null) return null;
        String access = JsonUtil.string(item, "access");
        if (access == null || !access.equals("playable")) return null;
        String title = JsonUtil.string(item, "title");
        if (title == null) return null;
        String id = JsonUtil.string(item, "id");
        if (id == null) {
            try {
                JsonElement raw = item.get("id");
                if (raw != null && raw.isJsonPrimitive() && raw.getAsJsonPrimitive().isNumber()) {
                    id = String.valueOf(raw.getAsLong());
                }
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        if (id == null) return null;
        JsonObject user = JsonUtil.object(item.get("user"));
        String creator = JsonUtil.string(user, "username");
        if (creator == null) creator = JsonUtil.string(item, "metadata_artist");
        String artist = creator == null ? "Unknown creator" : creator;
        String artwork = JsonUtil.string(item, "artwork_url");
        String url = JsonUtil.string(item, "permalink_url");
        String note = "SoundCloud track by " + artist + ". Source: SoundCloud. " + NOTE;
        long duration = JsonUtil.number(item, "duration", 0L);
        return new Track(id, title, artist, JsonUtil.string(item, "genre") == null ? "" : JsonUtil.string(item, "genre"),
                artwork == null ? "" : artwork, "", duration, "soundcloud", note, false);
    }
}
