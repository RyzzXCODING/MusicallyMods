package cc.alpoyy.musicallymods.music.impl;

import cc.alpoyy.musicallymods.music.MusicService;
import cc.alpoyy.musicallymods.music.ServiceCapabilities;
import cc.alpoyy.musicallymods.music.ServiceResult;
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
import java.util.concurrent.CompletableFuture;

/** Deezer's public search API. Only the official 30-second previews are played. */
public final class DeezerService implements MusicService {
    private static final String NOTE = "Official Deezer 30-second preview. Full songs are not streamed by this mod.";
    private static final ServiceCapabilities CAPABILITIES = new ServiceCapabilities(
            true, true, true, true, true, true, false, true,
            "Search, metadata and cover art. Plays official 30-second previews only - no full-song playback, playlists or premium content.");

    @Override
    public String id() {
        return "deezer";
    }

    @Override
    public String displayName() {
        return "Deezer previews";
    }

    @Override
    public ServiceCapabilities capabilities() {
        return CAPABILITIES;
    }

    @Override
    public String statusText() {
        return "Free public search API. No sign-in, no credentials. Previews only (30 seconds).";
    }

    @Override
    public CompletableFuture<ServiceResult<List<Track>>> search(String query) {
        String url = "https://api.deezer.com/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&limit=20";
        return NetworkManager.getString(url).thenApply(json -> {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray data = root.getAsJsonArray("data");
            if (data == null) {
                String apiError = JsonUtil.nestedString(root, "error", "message");
                return ServiceResult.failure(apiError != null ? apiError : "Deezer returned an invalid response.");
            }
            List<Track> tracks = new ArrayList<>();
            for (JsonElement element : data) {
                JsonObject item = JsonUtil.object(element);
                if (item == null) continue;
                String title = JsonUtil.string(item, "title");
                if (title == null) continue;
                String preview = JsonUtil.string(item, "preview");
                JsonObject album = JsonUtil.object(item.get("album"));
                String cover = firstNonBlank(JsonUtil.string(album, "cover_xl"), JsonUtil.string(album, "cover_big"),
                        JsonUtil.string(album, "cover_medium"), JsonUtil.string(album, "cover"));
                long seconds = JsonUtil.number(item, "duration", 0L);
                long duration = seconds > 0 ? Math.min(seconds * 1000L, 30_000L) : 30_000L;
                tracks.add(new Track(
                        String.valueOf(JsonUtil.number(item, "id", 0L)),
                        title,
                        orUnknown(JsonUtil.nestedString(item, "artist", "name")),
                        orUnknown(JsonUtil.string(album, "title")),
                        cover == null ? "" : cover,
                        preview == null ? "" : preview,
                        duration,
                        id(),
                        NOTE,
                        preview != null && !preview.isBlank()));
            }
            if (tracks.isEmpty()) return ServiceResult.failure("No Deezer tracks found for that search.");
            return ServiceResult.success(List.copyOf(tracks));
        }).exceptionally(throwable -> ServiceResult.failure("Deezer search failed: " + Errors.describe(throwable)));
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private static String orUnknown(String value) {
        return value == null || value.isBlank() ? "Unknown" : value;
    }
}
