package cc.alpoyy.musicallymods.audio;

import cc.alpoyy.musicallymods.music.Track;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Registry of optional, service-specific stream resolvers. */
public final class StreamResolvers {
    private static final Map<String, StreamResolver> RESOLVERS = new ConcurrentHashMap<>();

    private StreamResolvers() {
    }

    public static void register(String sourceId, StreamResolver resolver) {
        if (sourceId != null && !sourceId.isBlank() && resolver != null) {
            RESOLVERS.put(sourceId, resolver);
        }
    }

    public static String resolve(Track track, AtomicBoolean cancel) throws Exception {
        if (track == null) return null;
        StreamResolver resolver = RESOLVERS.get(track.sourceId());
        return resolver == null ? track.streamUrl() : resolver.resolve(track, cancel);
    }

    public static void cancel(Track track) {
        if (track == null) return;
        StreamResolver resolver = RESOLVERS.get(track.sourceId());
        if (resolver == null) return;
        try {
            resolver.cancel(track);
        } catch (RuntimeException ignored) {
            // Cancellation is best-effort teardown and must never fail a screen or tick.
        }
    }
}
