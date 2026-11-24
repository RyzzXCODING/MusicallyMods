package cc.alpoyy.musicallymods.lyrics;

import cc.alpoyy.musicallymods.music.ServiceResult;
import cc.alpoyy.musicallymods.music.Track;
import cc.alpoyy.musicallymods.util.Errors;
import cc.alpoyy.musicallymods.util.JsonUtil;
import cc.alpoyy.musicallymods.util.NetworkManager;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Anonymous LRCLIB lookup. No key, account, or credential is used or stored. */
public final class LrcLibService {
    private static final String ENDPOINT = "https://lrclib.net/api/get";

    private LrcLibService() {
    }

    public static CompletableFuture<ServiceResult<LyricsData>> fetch(Track track) {
        if (track == null || track.displayTitle().equals("Untitled")) {
            return CompletableFuture.completedFuture(ServiceResult.failure("No track metadata to look up."));
        }
        StringBuilder url = new StringBuilder(ENDPOINT)
                .append("?track_name=").append(encode(track.title()))
                .append("&artist_name=").append(encode(track.artist()));
        if (!track.album().isBlank() && !track.album().equals("Unknown")) {
            url.append("&album_name=").append(encode(track.album()));
        }
        if (track.durationMs() > 0) {
            url.append("&duration=").append(Math.round(track.durationMs() / 1000.0));
        }
        return NetworkManager.getString(url.toString()).<ServiceResult<LyricsData>>thenApply(json -> {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            String synced = JsonUtil.string(root, "syncedLyrics");
            String plain = JsonUtil.string(root, "plainLyrics");
            List<LyricLine> timed = LrcParser.parse(synced);
            if (!timed.isEmpty()) {
                return ServiceResult.success(new LyricsData(
                        valueOr(JsonUtil.string(root, "trackName"), track.title()),
                        valueOr(JsonUtil.string(root, "artistName"), track.artist()),
                        true, timed, "LRCLIB", ""));
            }
            List<LyricLine> untimed = splitPlain(plain);
            if (!untimed.isEmpty()) {
                return ServiceResult.success(new LyricsData(
                        valueOr(JsonUtil.string(root, "trackName"), track.title()),
                        valueOr(JsonUtil.string(root, "artistName"), track.artist()),
                        false, untimed, "LRCLIB", ""));
            }
            return ServiceResult.failure("No lyrics found for this track.");
        }).exceptionally(throwable -> ServiceResult.<LyricsData>failure("Lyrics lookup failed: " + Errors.describe(throwable)));
    }

    private static List<LyricLine> splitPlain(String plain) {
        List<LyricLine> lines = new ArrayList<>();
        if (plain == null || plain.isBlank()) return lines;
        for (String line : plain.split("\\R")) {
            if (!line.isBlank()) lines.add(new LyricLine(-1L, line));
        }
        return lines;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
