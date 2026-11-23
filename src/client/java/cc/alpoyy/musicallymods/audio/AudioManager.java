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
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Client-owned audio player. Streams are opened off the render thread; only the small state
 * snapshot is read by screens and HUD elements.
 */
public final class AudioManager {
    public enum State { STOPPED, LOADING, PLAYING, PAUSED, ERROR }

    private static final AudioManager INSTANCE = new AudioManager();
    private static final int BUFFER_BYTES = 8192;
    private static final ExecutorService LOADER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "musicallymods-audio");
        thread.setDaemon(true);
        return thread;
    });

    private final Object lock = new Object();
    private volatile State state = State.STOPPED;
    private volatile Track currentTrack;
    private volatile List<Track> queue = List.of();
    private volatile int queueIndex = -1;
    private volatile long positionMs;
    private volatile String error;
    private volatile float volume = ModConfig.get().volume;
    private volatile SourceDataLine line;
    private volatile long generation;
    private volatile AtomicBoolean activeCancel = new AtomicBoolean(true);
    private final AtomicBoolean paused = new AtomicBoolean(false);

    private AudioManager() {
    }

    public static AudioManager get() {
        return INSTANCE;
    }

    public void setQueue(List<Track> tracks) {
        synchronized (lock) {
            stopLocked();
            queue = tracks == null ? List.of() : List.copyOf(tracks);
            queueIndex = -1;
        }
    }

    public void play(Track track) {
        if (track == null) return;
        setQueue(List.of(track));
        playIndex(0);
    }

    public void playIndex(int index) {
        Track track;
        AtomicBoolean cancel;
        long gen;
        synchronized (lock) {
            if (index < 0 || index >= queue.size()) return;
            stopLocked();
            queueIndex = index;
            track = queue.get(index);
            currentTrack = track;
            state = State.LOADING;
            error = null;
            positionMs = 0;
            gen = ++generation;
            cancel = new AtomicBoolean(false);
            activeCancel = cancel;
        }
        LOADER.submit(() -> openAndPump(track, gen, cancel));
    }

    public void togglePlayPause() {
        State now = state;
        if (now == State.PLAYING) {
            pause();
        } else if (now == State.PAUSED) {
            resume();
        } else if (currentTrack != null) {
            play(currentTrack);
        } else if (!queue.isEmpty()) {
            playIndex(Math.max(0, queueIndex));
        }
    }

    public void pause() {
        synchronized (lock) {
            if (state != State.PLAYING) return;
            paused.set(true);
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
            paused.set(false);
            state = State.PLAYING;
            SourceDataLine activeLine = line;
            if (activeLine != null) activeLine.start();
        }
    }

    public void stop() {
        synchronized (lock) {
            stopLocked();
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
        float clamped = Math.max(0f, Math.min(1f, Float.isNaN(value) ? 0f : value));
        volume = clamped;
        ModConfig.get().volume = clamped;
        ModConfig.markDirty();
        SourceDataLine activeLine = line;
        if (activeLine != null) applyGain(activeLine, clamped);
    }

    public PlaybackSnapshot snapshot() {
        Track track = currentTrack;
        long duration = track == null ? 0L : track.durationMs();
        return new PlaybackSnapshot(state, track, positionMs, duration, error, queueIndex, queue.size(), volume);
    }

    public void shutdown() {
        synchronized (lock) {
            stopLocked();
        }
        LOADER.shutdownNow();
    }

    private void stopLocked() {
        generation++;
        activeCancel.set(true);
        paused.set(false);
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
        state = State.STOPPED;
        positionMs = 0;
        error = null;
    }

    private void openAndPump(Track track, long gen, AtomicBoolean cancel) {
        if (track == null || cancel.get() || gen != generation) return;
        if (!track.playable() || track.streamUrl().isBlank()) {
            fail(gen, track.note().isBlank() ? "This track does not provide a playable stream." : track.note());
            return;
        }
        Thread.currentThread().setName("musicallymods-audio");
        AudioInputStream base = null;
        AudioInputStream pcm = null;
        SourceDataLine activeLine = null;
        try {
            URL url = URI.create(track.streamUrl()).toURL();
            base = AudioSystem.getAudioInputStream(url);
            AudioFormat target = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED, 44100f, 16, 2, 4, 44100f, false);
            pcm = AudioSystem.getAudioInputStream(target, base);
            AudioFormat format = pcm.getFormat();
            activeLine = AudioSystem.getSourceDataLine(format);
            activeLine.open(format, BUFFER_BYTES);
            applyGain(activeLine, volume);
            activeLine.start();

            synchronized (lock) {
                if (cancel.get() || gen != generation) return;
                line = activeLine;
                state = State.PLAYING;
                error = null;
            }

            byte[] buffer = new byte[BUFFER_BYTES];
            int frameSize = Math.max(1, format.getFrameSize());
            float sampleRate = format.getSampleRate() > 0 ? format.getSampleRate() : 44100f;
            while (!cancel.get() && gen == generation) {
                if (paused.get()) {
                    sleep(10L);
                    continue;
                }
                int free = activeLine.available();
                if (free <= 0) {
                    sleep(2L);
                    continue;
                }
                int wanted = Math.min(buffer.length, free - free % frameSize);
                if (wanted <= 0) wanted = buffer.length;
                int read = pcm.read(buffer, 0, wanted);
                if (read < 0) break;
                if (read > 0) {
                    activeLine.write(buffer, 0, read);
                    positionMs += (long) (read / frameSize * 1000.0 / sampleRate);
                }
            }
            if (!cancel.get() && gen == generation) {
                activeLine.drain();
                synchronized (lock) {
                    if (!cancel.get() && gen == generation) {
                        state = State.STOPPED;
                        if (track.durationMs() > 0) positionMs = track.durationMs();
                    }
                }
            }
        } catch (UnsupportedAudioFileException e) {
            fail(gen, "Unsupported audio format. This stream's codec is not handled by the bundled decoders.");
        } catch (LineUnavailableException e) {
            fail(gen, "Audio line unavailable: " + Errors.describe(e));
        } catch (IOException e) {
            fail(gen, "Could not read audio stream: " + Errors.describe(e));
        } catch (RuntimeException e) {
            fail(gen, "Playback failed: " + Errors.describe(e));
        } finally {
            if (pcm != null) closeQuietly(pcm);
            if (base != null && base != pcm) closeQuietly(base);
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

    private void fail(long gen, String message) {
        synchronized (lock) {
            if (gen != generation) return;
            state = State.ERROR;
            error = message;
            positionMs = 0;
        }
    }

    private static void applyGain(SourceDataLine activeLine, float value) {
        try {
            if (!activeLine.isControlSupported(FloatControl.Type.MASTER_GAIN)) return;
            FloatControl gain = (FloatControl) activeLine.getControl(FloatControl.Type.MASTER_GAIN);
            float db = value <= 0f ? -60f : (float) (20.0 * Math.log10(value));
            gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), db)));
        } catch (RuntimeException ignored) {
            // Volume is still stored and applied to the next line.
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
