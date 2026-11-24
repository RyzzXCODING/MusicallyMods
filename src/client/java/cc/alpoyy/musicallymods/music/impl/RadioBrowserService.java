package cc.alpoyy.musicallymods.music.impl;

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
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/** Free, key-less radio station directory. Searches stations, not individual songs. */
public final class RadioBrowserService implements MusicService {
    private static final ServiceCapabilities CAPABILITIES = new ServiceCapabilities(
            true, true, true, true, true, true, false, false,
            "Search and play live stations. Searches station names/tags, not songs. MP3/OGG/WAV streams can play; other codecs are reported as unsupported. No song-level lyrics or playlists.");
    private static final String ENDPOINT = "https://de1.api.radio-browser.info/json/stations/search?name="
            + "%s&limit=20&hidebroken=true&order=votes&reverse=true";

    @Override
    public String id() {
        return "radio";
    }

    @Override
    public String displayName() {
        return "Internet Radio";
    }

    @Override
    public ServiceCapabilities capabilities() {
        return CAPABILITIES;
    }

    @Override
    public String statusText() {
        return "Radio Browser directory (free, no sign-in). Station search only; codec support varies.";
    }

    @Override
    public ServiceStatus status() {
        return new ServiceStatus(ServiceStatus.IntegrationLevel.IMPLEMENTED,
                ServiceStatus.PlaybackSupport.NATIVE, ServiceStatus.AuthRequirement.NONE,
                "Implemented - live station search and playback", CAPABILITIES.note());
    }

    @Override
    public CompletableFuture<ServiceResult<List<Track>>> search(String query) {
        String url = String.format(ENDPOINT, URLEncoder.encode(query, StandardCharsets.UTF_8));
        return NetworkManager.getString(url).<ServiceResult<List<Track>>>thenApply(json -> {
            JsonArray data = JsonParser.parseString(json).getAsJsonArray();
            List<Track> tracks = new ArrayList<>();
            for (JsonElement element : data) {
                JsonObject station = JsonUtil.object(element);
                if (station == null) continue;
                String name = JsonUtil.string(station, "name");
                if (name == null) continue;
                String stream = firstNonBlank(JsonUtil.string(station, "url_resolved"), JsonUtil.string(station, "url"));
                if (stream == null) continue;
                String codec = JsonUtil.string(station, "codec");
                if (codec == null) codec = "unknown";
                String country = JsonUtil.string(station, "country");
                String tags = JsonUtil.string(station, "tags");
                long bitrate = JsonUtil.number(station, "bitrate", 0L);
                String detail = codec.toUpperCase(Locale.ROOT) + (bitrate > 0 ? " / " + bitrate + " kbps" : "");
                String note = "Live station (" + detail + "). "
                        + (isSupported(codec) ? "Playable with bundled decoders." : "This codec is not supported here.");
                tracks.add(new Track(
                        orEmpty(JsonUtil.string(station, "stationuuid")),
                        name.trim(),
                        tags == null ? "Internet Radio" : tags,
                        country == null ? detail : country + " - " + detail,
                        orEmpty(JsonUtil.string(station, "favicon")),
                        stream,
                        0L,
                        id(),
                        note,
                        isSupported(codec)));
            }
            if (tracks.isEmpty()) return ServiceResult.failure("No live stations found for that search.");
            return ServiceResult.success(List.copyOf(tracks));
        }).exceptionally(throwable -> ServiceResult.<List<Track>>failure("Radio search failed: " + Errors.describe(throwable)));
    }

    private static boolean isSupported(String codec) {
        String value = codec.toLowerCase(Locale.ROOT);
        return value.equals("mp3") || value.equals("ogg") || value.equals("vorbis") || value.equals("wav");
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
