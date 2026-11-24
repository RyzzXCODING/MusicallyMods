package cc.alpoyy.musicallymods.audio;

import cc.alpoyy.musicallymods.config.ModConfig;
import cc.alpoyy.musicallymods.music.Track;
import cc.alpoyy.musicallymods.util.Errors;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLConnection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Client-owned audio player.
 *
 * <p>There is exactly one authoritative playback state. Background work is generation- and
 * cancellation-guarded; screens and the HUD only read an immutable snapshot. No playback is ever
 * simulated: PLAYING is set only after audio has actually been written to an open output line.</p>
 */
public final class AudioManager {
    public enum State { IDLE, LOADING, BUFFERING, PLAYING, PAUSED, STOPPED, FAILED }

    private static final AudioManager INSTANCE = new AudioManager();
    private static final int BUFFER_BYTES = 8192;
    private static final long STALL_CHECK_INTERVAL_MS = 500L;
    private static final ExecutorService LOADER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "musicallymods-audio");
        thread.setDaemon(true);
        return thread;
    });

    private final Object lock = new Object();
    private volatile State state = State.IDLE;
    private volatile Track currentTrack;
    private volatile List<Track> queue = List.of();
    private volatile int queueIndex = -1;
    private volatile long positionMs;
    private volatile String error;
    private volatile float volume = ModConfig.get().volume;
    private volatile SourceDataLine line;
    private volatile long generation;
    private volatile AtomicBoolean activeCancel = new AtomicBoolean(true);
    private volatile int retryAttempts;
    private boolean closed;

    private AudioManager() {
    }

    public static AudioManager get() {
        return INSTANCE;
    }

    /** Replaces the queue and returns to IDLE. */
    public void setQueue(List<Track> tracks) {
        synchronized (lock) {
            if (closed) return;
            stopLocked(State.IDLE);
            queue = tracks == null ? List.of() : List.copyOf(tracks);
            queueIndex = -1;
            currentTrack = null;
        }
    }

    public void play(Track track) {
        if (track == null) return;
        synchronized (lock) {
            if (closed) return;
            stopLocked(State.IDLE);
            queue = List.of(track);
            queueIndex = 0;
            beginLocked(track, 0);
        }
        submitCurrent();
    }

    public void playIndex(int index) {
        Track track;
        synchronized (lock) {
            if (closed || index < 0 || index >= queue.size()) return;
            track = queue.get(index);
            stopLocked(State.IDLE);
            queueIndex = index;
            beginLocked(track, 0);
        }
        submitCurrent();
    }

    /** Bounded manual retry, only available after a genuine failure. */
    public boolean retry() {
        Track track;
        synchronized (lock) {
            if (closed || state != State.FAILED || currentTrack == null) return false;
            if (retryAttempts >= maxRetryAttempts()) return false;
            track = currentTrack;
            retryAttempts++;
            beginLocked(track, retryAttempts);
        }
        submitCurrent();
        return true;
    }

    public void togglePlayPause() {
        switch (state) {
            case PLAYING, BUFFERING -> pause();
            case PAUSED -> resume();
            case FAILED -> retry();
            case IDLE, STOPPED -> {
                if (currentTrack != null) {
                    play(currentTrack);
                } else if (!queue.isEmpty()) {
                    playIndex(Math.max(0, queueIndex));
                }
            }
            case LOADING -> stop();
            default -> {
                // No action for an unknown future state.
            }
        }
    }

    public void pause() {
        synchronized (lock) {
            if (state != State.PLAYING && state != State.BUFFERING) return;
            state = State.PAUSED;
            SourceDataLine activeLine = line;
            if (activeLine != null) {
                activeLine.stop();
                activeLine.flush();
            }
        }
    }

    public void resume() {
        synchronized (lock) {
            if (state != State.PAUSED) return;
            state = State.BUFFERING;
            SourceDataLine activeLine = line;
            if (activeLine != null) activeLine.start();
        }
    }

    public void stop() {
        synchronized (lock) {
            stopLocked(State.STOPPED);
        }
    }

    /** Clears the current track and returns to IDLE without claiming a stopped playback. */
    public void clear() {
        synchronized (lock) {
            stopLocked(State.IDLE);
            currentTrack = null;
        }
    }

    public void next() {
        int next = -1;
        synchronized (lock) {
            if (!queue.isEmpty()) next = (queueIndex + 1) % queue.size();
        }
        if (next >= 0) playIndex(next);
    }

    public void previous() {
        int previous = -1;
        synchronized (lock) {
            if (!queue.isEmpty()) {
                int index = queueIndex <= 0 ? queue.size() - 1 : queueIndex - 1;
                previous = Math.max(0, index);
            }
        }
        if (previous >= 0) playIndex(previous);
    }

    public void setVolume(float value) {
        float clamped = Float.isFinite(value) ? Math.max(0f, Math.min(1f, value)) : 0f;
        volume = clamped;
        ModConfig.get().volume = clamped;
        ModConfig.markDirty();
        SourceDataLine activeLine = line;
        if (activeLine != null) applyGain(activeLine, clamped);
    }

    public float volume() {
        return volume;
    }

    public PlaybackSnapshot snapshot() {
        synchronized (lock) {
            return new PlaybackSnapshot(state, currentTrack, positionMs, durationOf(currentTrack), error,
                    queueIndex, queue.size(), volume, retryAttempts, maxRetryAttempts(), state == State.FAILED
                    && currentTrack != null && retryAttempts < maxRetryAttempts());
        }
    }

    public void shutdown() {
        synchronized (lock) {
            closed = true;
            stopLocked(State.IDLE);
            currentTrack = null;
        }
        LOADER.shutdownNow();
    }

    private void submitCurrent() {
        Track track;
        long gen;
        AtomicBoolean cancel;
        synchronized (lock) {
            track = currentTrack;
            gen = generation;
            cancel = activeCancel;
        }
        try {
            LOADER.execute(() -> openAndPump(track, gen, cancel));
        } catch (RejectedExecutionException e) {
            Errors.logTechnical("Audio worker rejected playback task", e);
            fail(gen, "The audio worker is unavailable. Reopen Minecraft to try again.");
        }
    }

    private void beginLocked(Track track, int attempts) {
        generation++;
        teardownAudioLocked();
        activeCancel = new AtomicBoolean(false);
        currentTrack = track;
        state = State.LOADING;
        error = null;
        positionMs = 0;
        retryAttempts = attempts;
    }

    private void stopLocked(State terminalState) {
        generation++;
        teardownAudioLocked();
        state = terminalState;
        positionMs = 0;
        error = null;
        retryAttempts = 0;
    }

    private void teardownAudioLocked() {
        activeCancel.set(true);
        SourceDataLine activeLine = line;
        line = null;
        if (activeLine != null) {
            try {
                activeLine.stop();
                activeLine.flush();
                activeLine.close();
            } catch (RuntimeException ignored) {
                // Closing a line during teardown must never crash Minecraft.
            }
        }
        StreamResolvers.cancel(currentTrack);
    }

    private void openAndPump(Track track, long gen, AtomicBoolean cancel) {
        Thread.currentThread().setName("musicallymods-audio");
        if (track == null || cancel.get() || gen != generation) return;
        if (!track.playable() || track.streamUrl().isBlank()) {
            fail(gen, track.note().isBlank() ? "This result does not provide a playable stream." : track.note());
            return;
        }

        String streamUrl;
        try {
            streamUrl = StreamResolvers.resolve(track, cancel);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail(gen, "Playback preparation was cancelled.");
            return;
        } catch (Exception e) {
            Errors.logTechnical("Stream resolution failed for " + track.sourceId(), e);
            fail(gen, "Could not prepare this stream: " + Errors.describe(e));
            return;
        }
        if (cancel.get() || gen != generation) return;
        if (streamUrl == null || streamUrl.isBlank()) {
            fail(gen, "This service did not provide a playable stream URL.");
            return;
        }

        URI uri;
        try {
            uri = URI.create(streamUrl);
            String scheme = uri.getScheme();
            if (uri.getHost() == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
                fail(gen, "Only legal http(s) audio streams can be played.");
                return;
            }
        } catch (IllegalArgumentException e) {
            Errors.logTechnical("Invalid stream URI", e);
            fail(gen, "The stream URL supplied by this service is invalid.");
            return;
        }

        AudioInputStream base = null;
        AudioInputStream pcm = null;
        SourceDataLine activeLine = null;
        InputStream networkStream = null;
        HttpURLConnection connection = null;
        try {
            connection = openConnection(uri, (int) stallTimeoutMs());
            networkStream = new BufferedInputStream(connection.getInputStream(), BUFFER_BYTES);
            base = AudioSystem.getAudioInputStream(networkStream);
            AudioFormat target = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED, 44100f, 16, 2, 4, 44100f, false);
            try {
                pcm = AudioSystem.getAudioInputStream(target, base);
            } catch (IllegalArgumentException e) {
                throw new UnsupportedAudioFileException("No PCM decoder for this audio format");
            }
            AudioFormat format = pcm.getFormat();
            activeLine = AudioSystem.getSourceDataLine(format);
            activeLine.open(format, BUFFER_BYTES);
            applyGain(activeLine, volume);
            activeLine.start();

            synchronized (lock) {
                if (!isActiveLocked(gen, cancel)) return;
                line = activeLine;
                state = State.BUFFERING;
                error = null;
            }

            byte[] buffer = new byte[BUFFER_BYTES];
            int frameSize = Math.max(1, format.getFrameSize());
            float sampleRate = format.getSampleRate() > 0 ? format.getSampleRate() : 44100f;
            long lastProgressNanos = System.nanoTime();
            long stallNanos = stallTimeoutMs() * 1_000_000L;
            long lastStallCheckNanos = lastProgressNanos;
            boolean announced = false;

            while (isActive(gen, cancel)) {
                if (state == State.PAUSED) {
                    sleep(10L);
                    continue;
                }
                int free = activeLine.available();
                if (free <= 0) {
                    if (announced) setStateIfActive(gen, cancel, State.BUFFERING);
                    sleep(2L);
                } else {
                    int wanted = Math.min(buffer.length, free - free % frameSize);
                    if (wanted <= 0) wanted = buffer.length;
                    int read = pcm.read(buffer, 0, wanted);
                    if (read < 0) break;
                    if (read > 0) {
                        activeLine.write(buffer, 0, read);
                        positionMs += (long) (read / frameSize * 1000.0 / sampleRate);
                        lastProgressNanos = System.nanoTime();
                        if (!announced) announced = true;
                        setStateIfActive(gen, cancel, State.PLAYING);
                    }
                }

                long now = System.nanoTime();
                if (now - lastStallCheckNanos >= STALL_CHECK_INTERVAL_MS * 1_000_000L) {
                    lastStallCheckNanos = now;
                    if (announced && now - lastProgressNanos > stallNanos) {
                        fail(gen, "This stream stopped providing audio. It may be offline or rate-limited.");
                        return;
                    }
                }
            }

            if (isActive(gen, cancel)) {
                activeLine.drain();
                synchronized (lock) {
                    if (isActiveLocked(gen, cancel)) {
                        state = State.STOPPED;
                        if (track.durationMs() > 0) positionMs = track.durationMs();
                    }
                }
            }
        } catch (UnsupportedAudioFileException e) {
            Errors.logTechnical("Unsupported audio format", e);
            fail(gen, "Unsupported audio format. This stream's codec cannot be decoded here.");
        } catch (LineUnavailableException e) {
            Errors.logTechnical("Audio line unavailable", e);
            fail(gen, "No audio output line is available. Check the game's audio device.");
        } catch (IOException e) {
            Errors.logTechnical("Audio stream I/O failed", e);
            fail(gen, "Could not read audio stream. The source may be offline or unavailable.");
        } catch (RuntimeException e) {
            Errors.logTechnical("Playback failed", e);
            fail(gen, "Playback failed: " + Errors.describe(e));
        } finally {
            if (pcm != null) closeQuietly(pcm);
            if (base != null && base != pcm) closeQuietly(base);
            if (networkStream != null && networkStream != base) closeQuietly(networkStream);
            if (connection != null) connection.disconnect();
            if (activeLine != null && activeLine.isOpen()) {
                try {
                    activeLine.stop();
                    activeLine.close();
                } catch (RuntimeException ignored) {
                    // Already closed during teardown.
                }
            }
            synchronized (lock) {
                if (line == activeLine) line = null;
            }
        }
    }

    /**
     * Opens an http(s) stream with bounded connect and read timeouts. Called only on the audio
     * worker; the connection is always disconnected by the caller's finally block.
     */
    private static HttpURLConnection openConnection(URI uri, int timeoutMs) throws IOException {
        URLConnection generic = uri.toURL().openConnection();
        if (!(generic instanceof HttpURLConnection http)) {
            throw new IOException("Only http(s) stream connections are supported");
        }
        try {
            http.setConnectTimeout(timeoutMs);
            http.setReadTimeout(timeoutMs);
            http.setInstanceFollowRedirects(true);
            http.connect();
            return http;
        } catch (IOException | RuntimeException e) {
            http.disconnect();
            throw e;
        }
    }

    private void fail(long gen, String message) {
        synchronized (lock) {
            if (gen != generation) return;
            state = State.FAILED;
            error = Errors.sanitize(message);
            positionMs = 0;
        }
    }

    private boolean isActive(long gen, AtomicBoolean cancel) {
        synchronized (lock) {
            return isActiveLocked(gen, cancel);
        }
    }

    private boolean isActiveLocked(long gen, AtomicBoolean cancel) {
        return !closed && !cancel.get() && gen == generation;
    }

    private void setStateIfActive(long gen, AtomicBoolean cancel, State next) {
        synchronized (lock) {
            if (isActiveLocked(gen, cancel) && state != State.PAUSED) state = next;
        }
    }

    private static long durationOf(Track track) {
        return track == null ? 0L : track.durationMs();
    }

    private static int maxRetryAttempts() {
        try {
            return Math.max(0, Math.min(5, ModConfig.get().playback.maxRetryAttempts));
        } catch (RuntimeException e) {
            return 2;
        }
    }

    private static long stallTimeoutMs() {
        try {
            return Math.max(5_000L, Math.min(120_000L, ModConfig.get().playback.requestTimeoutSeconds * 1000L));
        } catch (RuntimeException e) {
            return 20_000L;
        }
    }

    private static void applyGain(SourceDataLine activeLine, float value) {
        try {
            if (!ModConfig.get().playback.useMasterGain) return;
            if (!activeLine.isControlSupported(FloatControl.Type.MASTER_GAIN)) return;
            FloatControl gain = (FloatControl) activeLine.getControl(FloatControl.Type.MASTER_GAIN);
            float db = value <= 0f ? -60f : (float) (20.0 * Math.log10(value));
            gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), db)));
        } catch (RuntimeException ignored) {
            // Volume remains stored and is applied again when a new line opens.
        }
    }

    private static void closeQuietly(AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (Exception ignored) {
            // Best-effort cleanup only.
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
