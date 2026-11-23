package cc.alpoyy.musicallymods.audio;

import cc.alpoyy.musicallymods.music.Track;

/** Immutable view of the audio state for screens and the HUD. */
public record PlaybackSnapshot(
        AudioManager.State state,
        Track track,
        long positionMs,
        long durationMs,
        String error,
        int queueIndex,
        int queueSize,
        float volume
) {
    public boolean hasTrack() {
        return track != null;
    }

    public boolean isPlaying() {
        return state == AudioManager.State.PLAYING;
    }

    public boolean isLoading() {
        return state == AudioManager.State.LOADING;
    }

    public boolean isError() {
        return state == AudioManager.State.ERROR;
    }

    public float progress() {
        return durationMs > 0 ? Math.max(0f, Math.min(1f, (float) positionMs / durationMs)) : 0f;
    }
}
