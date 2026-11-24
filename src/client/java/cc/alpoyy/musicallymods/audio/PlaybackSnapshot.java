package cc.alpoyy.musicallymods.audio;

import cc.alpoyy.musicallymods.music.Track;

/** Immutable view of the single authoritative audio state for screens and the HUD. */
public record PlaybackSnapshot(
        AudioManager.State state,
        Track track,
        long positionMs,
        long durationMs,
        String error,
        int queueIndex,
        int queueSize,
        float volume,
        int retryAttempts,
        int maxRetryAttempts,
        boolean canRetry
) {
    public PlaybackSnapshot {
        if (state == null) state = AudioManager.State.IDLE;
        if (error == null) error = "";
        if (retryAttempts < 0) retryAttempts = 0;
        if (maxRetryAttempts < 0) maxRetryAttempts = 0;
    }

    public boolean hasTrack() {
        return track != null;
    }

    public boolean isPlaying() {
        return state == AudioManager.State.PLAYING;
    }

    public boolean isLoading() {
        return state == AudioManager.State.LOADING || state == AudioManager.State.BUFFERING;
    }

    public boolean isBuffering() {
        return state == AudioManager.State.BUFFERING;
    }

    public boolean isPaused() {
        return state == AudioManager.State.PAUSED;
    }

    public boolean isFailed() {
        return state == AudioManager.State.FAILED;
    }

    public boolean isStopped() {
        return state == AudioManager.State.STOPPED || state == AudioManager.State.IDLE;
    }

    public boolean hasError() {
        return !error.isBlank();
    }

    public float progress() {
        return durationMs > 0 ? Math.max(0f, Math.min(1f, (float) positionMs / durationMs)) : 0f;
    }

    public String stateLabel() {
        return switch (state) {
            case IDLE -> "Idle";
            case LOADING -> "Loading";
            case BUFFERING -> "Buffering";
            case PLAYING -> "Playing";
            case PAUSED -> "Paused";
            case STOPPED -> "Stopped";
            case FAILED -> "Failed";
        };
    }
}
