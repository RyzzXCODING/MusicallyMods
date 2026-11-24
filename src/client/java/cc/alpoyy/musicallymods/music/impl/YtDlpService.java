package cc.alpoyy.musicallymods.music.impl;

import cc.alpoyy.musicallymods.MusicallyMods;
import cc.alpoyy.musicallymods.audio.StreamResolver;
import cc.alpoyy.musicallymods.config.ModConfig;
import cc.alpoyy.musicallymods.music.MusicService;
import cc.alpoyy.musicallymods.music.ServiceCapabilities;
import cc.alpoyy.musicallymods.music.ServiceResult;
import cc.alpoyy.musicallymods.music.ServiceStatus;
import cc.alpoyy.musicallymods.music.Track;
import cc.alpoyy.musicallymods.util.Errors;
import cc.alpoyy.musicallymods.util.JsonUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Optional, opt-in YouTube Music bridge using a user-installed yt-dlp executable.
 *
 * <p>No binary is bundled, no account/cookie/token is used, and no access-control, DRM, Premium
 * or player-client bypass is attempted. It is disabled by default because YouTube's terms restrict
 * automated access and use of content outside the service.</p>
 */
public final class YtDlpService implements MusicService, StreamResolver {
    private static final String DISABLED =
            "YouTube Music playback is disabled. Enable the optional external bridge and configure a "
                    + "user-installed yt-dlp executable in config/musicallymods.json. Nothing is downloaded or bundled.";
    private static final String NO_EXECUTABLE =
            "The YouTube Music bridge is enabled, but no executable yt-dlp was found on PATH or in config. "
                    + "Playback is not attempted until you install it yourself.";
    private static final String NOTE =
            "Unofficial opt-in bridge. Not YouTube support; no login, cookies, Premium or bypass. "
                    + "Extraction is fragile and may conflict with YouTube's terms.";
    private static final List<String> COMMON_FLAGS = List.of(
            "--no-config", "--no-plugin-dirs", "--no-remote-components", "--no-cache-dir",
            "--no-update", "--no-progress", "--no-warnings", "--no-part", "--no-mtime",
            "--socket-timeout", "10", "--retries", "1", "--extractor-retries", "1",
            "--fragment-retries", "1");

    private final Map<String, Process> activeProcesses = new ConcurrentHashMap<>();

    @Override
    public String id() {
        return "youtubemusic";
    }

    @Override
    public String displayName() {
        return "YouTube Music (external bridge)";
    }

    @Override
    public ServiceCapabilities capabilities() {
        boolean available = enabled() && YtDlpProcess.findExecutable().isPresent();
        return new ServiceCapabilities(
                true, true, true, available, true, true, false, false,
                available ? NOTE : (enabled() ? NO_EXECUTABLE : DISABLED));
    }

    @Override
    public String statusText() {
        return status().summary();
    }

    @Override
    public ServiceStatus status() {
        ModConfig.Integrations cfg = ModConfig.get().integrations;
        if (!cfg.youTubeMusicBridgeEnabled) {
            return new ServiceStatus(ServiceStatus.IntegrationLevel.NOT_INTEGRATED,
                    ServiceStatus.PlaybackSupport.UNSUPPORTED,
                    ServiceStatus.AuthRequirement.USER_CONFIGURED_EXECUTABLE,
                    "Not integrated by default - optional external bridge available",
                    DISABLED);
        }
        if (YtDlpProcess.findExecutable().isEmpty()) {
            return new ServiceStatus(ServiceStatus.IntegrationLevel.OPT_IN_EXTERNAL_BRIDGE,
                    ServiceStatus.PlaybackSupport.EXTERNAL_BRIDGE,
                    ServiceStatus.AuthRequirement.USER_CONFIGURED_EXECUTABLE,
                    "Bridge enabled - yt-dlp executable not found",
                    NO_EXECUTABLE);
        }
        return new ServiceStatus(ServiceStatus.IntegrationLevel.OPT_IN_EXTERNAL_BRIDGE,
                ServiceStatus.PlaybackSupport.EXTERNAL_BRIDGE,
                ServiceStatus.AuthRequirement.USER_CONFIGURED_EXECUTABLE,
                "Optional yt-dlp bridge active (unofficial, ToS-gray)", NOTE);
    }

    @Override
    public CompletableFuture<ServiceResult<List<Track>>> search(String query) {
        if (query == null || query.isBlank()) {
            return CompletableFuture.completedFuture(ServiceResult.failure("Enter a search query first."));
        }
        if (!enabled()) {
            return CompletableFuture.completedFuture(ServiceResult.failure(DISABLED));
        }
        Path executable = YtDlpProcess.findExecutable().orElse(null);
        if (executable == null) {
            return CompletableFuture.completedFuture(ServiceResult.failure(NO_EXECUTABLE));
        }
        return CompletableFuture.supplyAsync(() -> searchBlocking(query.trim(), executable),
                YtDlpProcess.searchExecutor())
                .exceptionally(throwable -> ServiceResult.failure(
                        "YouTube Music bridge failed: " + Errors.describe(throwable)));
    }

    @Override
    public String resolve(Track track, AtomicBoolean cancel) throws Exception {
        if (track == null) throw new IOException("No track to resolve.");
        if (!enabled()) throw new IllegalStateException(DISABLED);
        Path executable = YtDlpProcess.findExecutable().orElse(null);
        if (executable == null) throw new IllegalStateException(NO_EXECUTABLE);

        List<String> command = baseCommand(executable);
        command.add("--no-playlist");
        command.add("--dump-single-json");
        command.add("--skip-download");
        command.add("-f");
        command.add("bestaudio/best[acodec!=none]");
        command.add("--");
        command.add(track.streamUrl());

        String key = key(track);
        YtDlpProcess.Result result = YtDlpProcess.run(command, cancel, timeoutSeconds(),
                process -> activeProcesses.put(key, process));
        try {
            if (!result.ok()) {
                throw new IOException(failureMessage(result));
            }
            JsonObject root = parse(result.stdout());
            String url = JsonUtil.string(root, "url");
            if (url == null || !(url.startsWith("https://") || url.startsWith("http://"))) {
                throw new IOException("yt-dlp returned no playable http(s) audio URL.");
            }
            return url;
        } finally {
            activeProcesses.remove(key);
        }
    }

    @Override
    public void cancel(Track track) {
        if (track == null) return;
        Process process = activeProcesses.remove(key(track));
        if (process == null) return;
        try {
            process.descendants().forEach(ProcessHandle::destroy);
            process.destroy();
        } catch (RuntimeException ignored) {
            // Teardown is best effort.
        }
    }

    private ServiceResult<List<Track>> searchBlocking(String query, Path executable) {
        String searchUrl = "https://music.youtube.com/search?q=" + encode(query) + "#songs";
        List<String> command = baseCommand(executable);
        command.add("--flat-playlist");
        command.add("--playlist-end");
        command.add("10");
        command.add("--dump-single-json");
        command.add("--skip-download");
        command.add("--");
        command.add(searchUrl);

        YtDlpProcess.Result result = YtDlpProcess.run(command, null, timeoutSeconds(), null);
        if (!result.ok()) {
            MusicallyMods.LOGGER.debug("[MusicallyMods] yt-dlp search failed: exit {} - {}",
                    result.exitCode(), Errors.sanitize(tail(result.stderr(), 300)));
            return ServiceResult.failure(failureMessage(result));
        }

        JsonObject root = parse(result.stdout());
        JsonArray entries = root.getAsJsonArray("entries");
        List<Track> tracks = new ArrayList<>();
        if (entries != null) {
            for (JsonElement element : entries) {
                JsonObject entry = JsonUtil.object(element);
                if (entry == null) continue;
                Track track = toTrack(entry);
                if (track == null) continue;
                tracks.add(track);
            }
        } else {
            Track single = toTrack(root);
            if (single != null) tracks.add(single);
        }
        if (tracks.isEmpty()) {
            return ServiceResult.failure("No YouTube Music results were returned by the optional bridge.");
        }
        return ServiceResult.success(List.copyOf(tracks));
    }

    private static Track toTrack(JsonObject entry) {
        String id = JsonUtil.string(entry, "id");
        String title = JsonUtil.string(entry, "title");
        if ((id == null || id.isBlank()) && (title == null || title.isBlank())) return null;
        if (id == null || id.isBlank()) id = title;
        if (title == null || title.isBlank()) title = id;
        String artist = firstNonBlank(JsonUtil.string(entry, "channel"),
                JsonUtil.string(entry, "uploader"), JsonUtil.string(entry, "artist"));
        String webpage = JsonUtil.string(entry, "webpage_url");
        if (webpage == null || webpage.isBlank()) webpage = "https://www.youtube.com/watch?v=" + id;
        long duration = JsonUtil.number(entry, "duration", 0L) * 1000L;
        String artwork = firstImage(entry);
        return new Track(id, title, artist == null ? "Unknown artist" : artist,
                "YouTube Music", artwork == null ? "" : artwork, webpage, duration, "youtubemusic", NOTE, true);
    }

    private static String firstImage(JsonObject entry) {
        JsonArray thumbnails = entry.getAsJsonArray("thumbnails");
        if (thumbnails == null) return null;
        for (int i = thumbnails.size() - 1; i >= 0; i--) {
            JsonObject thumbnail = JsonUtil.object(thumbnails.get(i));
            String url = JsonUtil.string(thumbnail, "url");
            if (url != null && url.startsWith("https://")) return url;
        }
        return null;
    }

    private static JsonObject parse(String json) throws IOException {
        try {
            JsonElement parsed = JsonParser.parseString(json);
            return parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
        } catch (RuntimeException e) {
            throw new IOException("yt-dlp returned invalid JSON.", e);
        }
    }

    private static List<String> baseCommand(Path executable) {
        List<String> command = new ArrayList<>();
        command.add(executable.toString());
        command.addAll(COMMON_FLAGS);
        return command;
    }

    private static String failureMessage(YtDlpProcess.Result result) {
        if (result.cancelled()) return "yt-dlp extraction was cancelled.";
        if (result.timedOut()) return "yt-dlp extraction timed out; the source may be unavailable.";
        String detail = Errors.sanitize(tail(result.stderr(), 220));
        return "yt-dlp failed (exit " + result.exitCode() + ")"
                + (detail.isBlank() ? "." : ": " + detail);
    }

    private static String tail(String value, int max) {
        if (value == null) return "";
        String text = value.trim();
        return text.length() <= max ? text : text.substring(text.length() - max);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static boolean enabled() {
        return ModConfig.get().integrations.youTubeMusicBridgeEnabled;
    }

    private static int timeoutSeconds() {
        return Math.max(10, Math.min(120, ModConfig.get().playback.requestTimeoutSeconds));
    }

    private static String key(Track track) {
        String key = track.id();
        return key.isBlank() ? track.streamUrl() : key;
    }
}
