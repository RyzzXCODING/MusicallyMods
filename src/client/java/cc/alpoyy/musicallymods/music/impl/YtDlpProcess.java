package cc.alpoyy.musicallymods.music.impl;

import cc.alpoyy.musicallymods.util.Errors;
import net.fabricmc.loader.api.FabricLoader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Bounded, cancellable execution of a user-installed yt-dlp executable.
 *
 * <p>No executable is bundled. No shell is used. Credential flags (cookies, browser stores,
 * login, PO tokens, impersonation, access-control bypass) are deliberately absent.</p>
 */
public final class YtDlpProcess {
    public record Result(int exitCode, String stdout, String stderr, boolean timedOut, boolean cancelled) {
        public boolean ok() {
            return exitCode == 0 && !timedOut && !cancelled;
        }
    }

    private static final int MAX_OUTPUT_BYTES = 256 * 1024;
    private static final long WAIT_SLICE_MS = 100L;
    private static final long TEARDOWN_WAIT_SECONDS = 2L;
    private static final ExecutorService SEARCH_POOL = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "musicallymods-ytdlp");
        thread.setDaemon(true);
        return thread;
    });

    private YtDlpProcess() {
    }

    public static ExecutorService searchExecutor() {
        return SEARCH_POOL;
    }

    public static void shutdown() {
        SEARCH_POOL.shutdownNow();
        YtDlpManager.shutdown();
    }

    /** Delegates to the manager so every caller shares one OS-aware, cached detection result. */
    public static Optional<Path> findExecutable() {
        return YtDlpManager.executable();
    }

    public static Result run(List<String> command, AtomicBoolean cancel, int timeoutSeconds) {
        return run(command, cancel, timeoutSeconds, null);
    }

    public static Result run(List<String> command, AtomicBoolean cancel, int timeoutSeconds,
                             Consumer<Process> onStart) {
        if (command == null || command.isEmpty()) {
            return new Result(2, "", "Empty yt-dlp command", false, false);
        }
        ProcessBuilder builder = new ProcessBuilder(command)
                .redirectErrorStream(false)
                .redirectInput(ProcessBuilder.Redirect.DISCARD);
        try {
            builder.directory(FabricLoader.getInstance().getGameDir().toFile());
        } catch (RuntimeException ignored) {
            // A missing or inaccessible game directory falls back to the JVM working directory.
        }
        Process process = null;
        Thread stdoutReader = null;
        Thread stderrReader = null;
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        boolean[] stdoutTruncated = {false};
        boolean[] stderrTruncated = {false};
        boolean timedOut = false;
        boolean cancelled = false;
        int exitCode = -1;

        try {
            process = builder.start();
            if (onStart != null) onStart.accept(process);
            Process running = process;
            stdoutReader = new Thread(() -> drain(running.getInputStream(), stdout, stdoutTruncated),
                    "musicallymods-ytdlp-stdout");
            stderrReader = new Thread(() -> drain(running.getErrorStream(), stderr, stderrTruncated),
                    "musicallymods-ytdlp-stderr");
            stdoutReader.setDaemon(true);
            stderrReader.setDaemon(true);
            stdoutReader.start();
            stderrReader.start();

            long deadline = System.nanoTime() + Math.max(5L, Math.min(120L, timeoutSeconds)) * 1_000_000_000L;
            while (process.isAlive()) {
                if (cancel != null && cancel.get()) {
                    cancelled = true;
                    break;
                }
                if (System.nanoTime() >= deadline) {
                    timedOut = true;
                    break;
                }
                try {
                    process.waitFor(WAIT_SLICE_MS, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    cancelled = true;
                    break;
                }
            }

            if (process.isAlive()) destroy(process);
            try {
                if (!process.waitFor(TEARDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
                    process.descendants().forEach(ProcessHandle::destroyForcibly);
                    process.destroyForcibly();
                    process.waitFor(TEARDOWN_WAIT_SECONDS, TimeUnit.SECONDS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
            exitCode = process.isAlive() ? -1 : process.exitValue();
        } catch (IOException | RuntimeException e) {
            Errors.logTechnical("Could not start yt-dlp", e);
            if (process != null) destroy(process);
            return new Result(2, "", "Could not start the configured yt-dlp executable: " + Errors.describe(e),
                    false, cancelled);
        } finally {
            joinQuietly(stdoutReader);
            joinQuietly(stderrReader);
            if (process != null) {
                try {
                    process.getInputStream().close();
                    process.getErrorStream().close();
                } catch (IOException ignored) {
                    // Streams may already be closed by process teardown.
                }
            }
        }

        String out = stdout.toString(StandardCharsets.UTF_8);
        String err = stderr.toString(StandardCharsets.UTF_8);
        if (stdoutTruncated[0]) out += "\n[output truncated]";
        if (stderrTruncated[0]) err += "\n[output truncated]";
        return new Result(exitCode, out, err, timedOut, cancelled);
    }

    private static void destroy(Process process) {
        try {
            process.descendants().forEach(ProcessHandle::destroy);
            process.destroy();
        } catch (RuntimeException ignored) {
            // Best-effort cleanup.
        }
    }

    private static void drain(InputStream input, ByteArrayOutputStream sink, boolean[] truncated) {
        byte[] buffer = new byte[8192];
        try (InputStream stream = input) {
            int read;
            while ((read = stream.read(buffer)) >= 0) {
                int room = MAX_OUTPUT_BYTES - sink.size();
                if (room > 0) sink.write(buffer, 0, Math.min(read, room));
                if (read > room) truncated[0] = true;
            }
        } catch (IOException ignored) {
            // Process teardown closes the pipes; partial output is still useful.
        }
    }

    private static void joinQuietly(Thread thread) {
        if (thread == null) return;
        try {
            thread.join(TEARDOWN_WAIT_SECONDS * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
