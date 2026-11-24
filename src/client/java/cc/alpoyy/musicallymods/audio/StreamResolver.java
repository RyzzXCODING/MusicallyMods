package cc.alpoyy.musicallymods.audio;

import cc.alpoyy.musicallymods.music.Track;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Resolves a service-specific track into a playable http(s) URL immediately before playback.
 * Implementations must be bounded and cancellable because they run on the audio worker thread.
 */
public interface StreamResolver {
    String resolve(Track track, AtomicBoolean cancel) throws Exception;

    /** Called when playback is stopped or superseded; must never throw. */
    default void cancel(Track track) {
    }
}
