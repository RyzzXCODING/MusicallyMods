package cc.alpoyy.musicallymods.lyrics;

import cc.alpoyy.musicallymods.config.ModConfig;
import cc.alpoyy.musicallymods.music.ServiceResult;
import cc.alpoyy.musicallymods.music.Track;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Owns the current lyrics and a small bounded cache. Never invents timing data. */
public final class LyricsManager {
    private static final int MAX_CACHE = 32;
    private static final LyricsManager INSTANCE = new LyricsManager();
    private final Map<String, LyricsData> cache = Collections.synchronizedMap(
            new LinkedHashMap<>(MAX_CACHE, 0.75f, true));
    private volatile LyricsData current = LyricsData.empty("");
    private volatile Track currentTrack;
    private volatile boolean loading;
    private volatile long generation;

    private LyricsManager() {
    }

    public static LyricsManager get() {
        return INSTANCE;
    }

    public void setTrack(Track track) {
        if (sameTrack(currentTrack, track)) return;
        currentTrack = track;
        long gen = ++generation;
        loading = false;
        if (!canLookup(track)) {
            current = LyricsData.empty("Lyrics lookup needs a metadata track from Deezer. Live stations and direct URLs have no song-level lyrics.");
            return;
        }
        String key = key(track);
        LyricsData cached = cache.get(key);
        if (cached != null) {
            current = cached;
            return;
        }
        current = LyricsData.empty("");
        loading = true;
        LrcLibService.fetch(track).whenComplete((result, throwable) -> {
            if (gen != generation) return;
            loading = false;
            if (throwable != null || result == null || !result.successful()) {
                String message = throwable != null ? "Lyrics lookup failed." : result.error();
                current = LyricsData.empty(message);
                return;
            }
            LyricsData data = result.value();
            if (data == null || data.isEmpty()) {
                current = LyricsData.empty("No lyrics found for this track.");
                return;
            }
            if (cache.size() >= MAX_CACHE) {
                synchronized (cache) {
                    String oldest = cache.keySet().iterator().next();
                    cache.remove(oldest);
                }
            }
            cache.put(key, data);
            current = data;
        });
    }

    public LyricsData current() {
        return current;
    }

    public Track currentTrack() {
        return currentTrack;
    }

    public boolean isLoading() {
        return loading;
    }

    public int currentIndex(long positionMs) {
        LyricsData data = current;
        if (!data.synced() || positionMs < 0) return -1;
        int found = -1;
        List<LyricLine> lines = data.lines();
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).timeMs() <= positionMs) found = i;
            else break;
        }
        return found;
    }

    public void toggleEnabled() {
        ModConfig.get().lyrics.enabled = !ModConfig.get().lyrics.enabled;
        ModConfig.markDirty();
    }

    public void clear() {
        currentTrack = null;
        generation++;
        loading = false;
        current = LyricsData.empty("");
    }

    private static boolean canLookup(Track track) {
        return track != null
                && "deezer".equals(track.sourceId())
                && !track.title().isBlank()
                && !track.artist().isBlank();
    }

    private static boolean sameTrack(Track a, Track b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.id().equals(b.id()) && a.title().equals(b.title()) && a.artist().equals(b.artist());
    }

    private static String key(Track track) {
        return (track.title() + "\u0000" + track.artist() + "\u0000" + track.album())
                .toLowerCase(Locale.ROOT);
    }
}
