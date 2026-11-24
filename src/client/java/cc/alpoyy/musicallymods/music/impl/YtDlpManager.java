package cc.alpoyy.musicallymods.music.impl;

import cc.alpoyy.musicallymods.config.ModConfig;
import cc.alpoyy.musicallymods.util.Errors;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Owns yt-dlp detection, health checks, diagnostics and opt-in installation.
 *
 * <p>Nothing is bundled or executed silently. The resolver path is never passed through a shell;
 * installation only completes after the downloaded bytes match the official checksum file.</p>
 */
public final class YtDlpManager {
    public enum Status {
        NOT_INSTALLED, DETECTING, DETECTED, READY, INCOMPATIBLE, PERMISSION_ERROR, MISSING, FAILED
    }

    public record Diagnostic(Status status, String version, Path path, String source,
                             String lastError, long checkedAt) {
        public Diagnostic {
            version = version == null ? "" : version;
            source = source == null ? "" : source;
            lastError = lastError == null ? "" : lastError;
        }

        public boolean usable() {
            return (status == Status.READY || status == Status.DETECTED) && path != null;
        }
    }

    public record DownloadProgress(long downloaded, long total, String stage, boolean cancelled) {
        public DownloadProgress {
            stage = stage == null ? "" : stage;
        }
    }

    public record InstallResult(boolean success, String message) {
        public InstallResult {
            message = message == null ? "" : message;
        }
    }

    /** Oldest release whose command-line surface still matches the flags used by this mod. */
    private static final String MINIMUM_VERSION = "2023.01.01";
    private static final String RELEASE_BASE = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/";
    private static final long DETECTION_TTL_NANOS = 10L * 1_000_000_000L;
    private static final int VERSION_TIMEOUT_SECONDS = 10;
    private static final long MAX_DOWNLOAD_BYTES = 48L * 1024L * 1024L;

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "musicallymods-ytdlp-manager");
        thread.setDaemon(true);
        return thread;
    });

    private static volatile Diagnostic cached = new Diagnostic(
            Status.DETECTING, "", null, "not checked yet", "", 0L);
    private static volatile Detection detected;
    private static volatile long detectedAtNanos;
    private static volatile boolean installedAssetValid = false;

    private record Detection(Path path, Status fallback, String source, String detail) {
    }

    private YtDlpManager() {
    }

    /** Filesystem-only lookup; safe to call while rendering because it never starts a process. */
    public static Optional<Path> executable() {
        Diagnostic diagnostic = diagnostic();
        return diagnostic.usable() ? Optional.of(diagnostic.path()) : Optional.empty();
    }

    /** Returns the last health result combined with the current, cheaply revalidated detection. */
    public static Diagnostic diagnostic() {
        Detection current = detection();
        Diagnostic last = cached;
        if (current.path() == null) {
            return new Diagnostic(current.fallback(), "", null, current.source(),
                    current.detail(), System.nanoTime());
        }
        if (last.status() == Status.DETECTING && last.path() == null) {
            return new Diagnostic(Status.DETECTED, "", current.path(), current.source(),
                    "Version check has not completed yet.", System.nanoTime());
        }
        if (last.path() != null && last.path().equals(current.path())) return last;
        return new Diagnostic(Status.DETECTED, "", current.path(), current.source(),
                "Executable found; run Retry Detection to verify its version.", System.nanoTime());
    }

    /** Clears detection and health caches so the next status query probes the filesystem again. */
    public static void clearCache() {
        detected = null;
        detectedAtNanos = 0L;
        cached = new Diagnostic(Status.DETECTING, "", null, "not checked yet", "", System.nanoTime());
    }

    /** Saves a user-provided absolute or game-relative path and rechecks it. */
    public static CompletableFuture<Diagnostic> configurePath(String rawPath) {
        ModConfig.get().integrations.youTubeMusicBridgeExecutable = sanitized(rawPath);
        ModConfig.markDirty();
        clearCache();
        return refreshAsync();
    }

    /** Runs a bounded {@code --version} check off-thread and publishes the resulting diagnostic. */
    public static CompletableFuture<Diagnostic> refreshAsync() {
        clearCache();
        Detection current = detection();
        cached = new Diagnostic(Status.DETECTING, "", current.path(), current.source(),
                "Checking the executable...", System.nanoTime());
        if (current.path() == null) {
            Diagnostic result = new Diagnostic(current.fallback(), "", null, current.source(),
                    current.detail(), System.nanoTime());
            cached = result;
            return CompletableFuture.completedFuture(result);
        }
        return CompletableFuture.supplyAsync(() -> healthCheck(current), WORKER)
                .thenApply(result -> {
                    cached = result;
                    return result;
                })
                .exceptionally(throwable -> {
                    Diagnostic result = new Diagnostic(Status.FAILED, "", current.path(), current.source(),
                            Errors.describe(throwable), System.nanoTime());
                    cached = result;
                    return result;
                });
    }

    /** Mod-managed, opt-in installation directory; never the config file or the main config directory. */
    public static Path dependencyDirectory() {
        return FabricLoader.getInstance().getGameDir()
                .resolve("musicallymods").resolve("dependencies").resolve("yt-dlp");
    }

    /** Downloads a named official release asset only after the caller has confirmed the action. */
    public static CompletableFuture<InstallResult> installAsync(AtomicBoolean cancel,
                                                                Consumer<DownloadProgress> progress) {
        if (cancel == null) cancel = new AtomicBoolean(false);
        if (cancel.get()) {
            return CompletableFuture.completedFuture(new InstallResult(false, "Installation cancelled."));
        }
        String asset = installAssetName();
        if (asset == null) {
            return CompletableFuture.completedFuture(new InstallResult(false,
                    "This CPU architecture has no official standalone release asset. Install yt-dlp manually "
                            + "from the official release page and configure its path."));
        }
        installedAssetValid = false;
        return CompletableFuture.supplyAsync(() -> {
            byte[] checksums;
            byte[] binary;
            try {
                publish(progress, 0, 1, "Fetching official checksums", cancel.get());
                checksums = cc.alpoyy.musicallymods.util.NetworkManager.download(
                        RELEASE_BASE + "SHA2-256SUMS", 512L * 1024L, cancel, null).join();
                if (cancel.get()) return cancelled();

                publish(progress, 0, 1, "Fetching official yt-dlp release", cancel.get());
                binary = cc.alpoyy.musicallymods.util.NetworkManager.download(
                        RELEASE_BASE + asset, MAX_DOWNLOAD_BYTES, cancel,
                        downloaded -> publish(progress, downloaded, -1, "Downloading", cancel.get())).join();
                if (cancel.get()) return cancelled();

                String expected = checksumFor(checksums, asset);
                if (expected == null) {
                    return new InstallResult(false,
                            "The official checksum file has no entry for " + asset + "; nothing was installed.");
                }
                if (!matches(expected, binary)) {
                    return new InstallResult(false,
                            "Checksum verification failed. The downloaded file was discarded.");
                }
                return installVerified(binary, asset, progress, cancel);
            } catch (IOException | RuntimeException e) {
                Errors.logTechnical("yt-dlp install failed", e);
                return new InstallResult(false,
                        "Installation failed: " + Errors.describe(e) + ". No executable was changed.");
            }
        }, WORKER).thenApply(result -> {
            if (result.success()) {
                installedAssetValid = true;
                clearCache();
            }
            return result;
        });
    }

    /** True when the last official checksum verification succeeded; exposed for honest GUI wording. */
    public static boolean lastInstallVerified() {
        return installedAssetValid;
    }

    /** Cancels any in-flight version check or installation without touching saved settings. */
    public static void shutdown() {
        WORKER.shutdownNow();
    }

    private static InstallResult installVerified(byte[] binary, String asset,
                                                 Consumer<DownloadProgress> progress,
                                                 AtomicBoolean cancel) {
        try {
            if (cancel.get()) return cancelled();
            Path directory = dependencyDirectory();
            Files.createDirectories(directory);
            Path target = directory.resolve(asset).normalize();
            if (!target.startsWith(directory)) {
                return new InstallResult(false, "Refusing to write outside the dependency directory.");
            }
            Path temp = Files.createTempFile(directory, "yt-dlp-", ".part");
            try {
                Files.write(temp, binary);
                makeExecutable(temp);
                if (cancel.get()) return cancelled();
                try {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temp);
            }
            publish(progress, binary.length, binary.length, "Installed and verified", cancel.get());
            return new InstallResult(true, "Installed " + asset + " from the official release after checksum verification.");
        } catch (IOException | RuntimeException e) {
            Errors.logTechnical("yt-dlp install write failed", e);
            installedAssetValid = false;
            return new InstallResult(false, "Could not store the verified executable: " + Errors.describe(e));
        }
    }

    private static void makeExecutable(Path path) throws IOException {
        try {
            Files.setPosixFilePermissions(path,
                    PosixFilePermissions.fromString("rwxr--r--"));
        } catch (RuntimeException ignored) {
            // Windows and non-POSIX file systems do not use Unix permission bits.
        }
    }

    private static Diagnostic healthCheck(Detection current) {
        Path path = current.path();
        YtDlpProcess.Result result = YtDlpProcess.run(
                List.of(path.toString(), "--version"), null, VERSION_TIMEOUT_SECONDS);
        if (result.cancelled()) {
            return new Diagnostic(Status.FAILED, "", path, current.source(), "Version check was cancelled.",
                    System.nanoTime());
        }
        if (result.timedOut()) {
            return new Diagnostic(Status.FAILED, "", path, current.source(),
                    "The executable did not answer a version check in time.", System.nanoTime());
        }
        if (!result.ok()) {
            String detail = Errors.sanitize(tail(result.stderr(), 200));
            String hint = detail.toLowerCase(Locale.ROOT);
            if (hint.contains("permission denied") || hint.contains("access is denied")
                    || hint.contains("not executable") || hint.contains("cannot run")) {
                return new Diagnostic(Status.PERMISSION_ERROR, "", path, current.source(),
                        "Found " + path + " but it could not be executed: " + detail, System.nanoTime());
            }
            return new Diagnostic(Status.FAILED, "", path, current.source(),
                    "Executable failed a version check (exit " + result.exitCode() + ")"
                            + (detail.isBlank() ? "." : ": " + detail), System.nanoTime());
        }
        String version = parseVersion(result.stdout());
        if (version == null) {
            return new Diagnostic(Status.INCOMPATIBLE, "", path, current.source(),
                    "The executable did not report a recognizable yt-dlp version.", System.nanoTime());
        }
        if (compareDates(version, MINIMUM_VERSION) < 0) {
            return new Diagnostic(Status.INCOMPATIBLE, version, path, current.source(),
                    "yt-dlp " + version + " is too old (minimum " + MINIMUM_VERSION + ").", System.nanoTime());
        }
        return new Diagnostic(Status.READY, version, path, current.source(), "", System.nanoTime());
    }

    private static Detection detection() {
        long now = System.nanoTime();
        Detection cachedDetection = detected;
        if (cachedDetection != null && now - detectedAtNanos < DETECTION_TTL_NANOS) {
            return cachedDetection;
        }
        Detection result = detectNow();
        detected = result;
        detectedAtNanos = now;
        return result;
    }

    private static Detection detectNow() {
        String configured = ModConfig.get().integrations.youTubeMusicBridgeExecutable;
        List<Path> candidates = new ArrayList<>();
        if (configured != null && !configured.isBlank()) {
            Path path = resolveUserPath(configured);
            if (path != null) candidates.add(path);
        }
        Path managed = dependencyDirectory();
        for (String name : candidateNames()) candidates.add(managed.resolve(name));
        for (Path directory : searchDirectories()) {
            for (String name : candidateNames()) candidates.add(directory.resolve(name));
        }

        Path permissionDenied = null;
        for (Path candidate : candidates) {
            Path absolute = safeAbsolute(candidate);
            if (absolute == null || !Files.isRegularFile(absolute)) continue;
            boolean executable = looksExecutable(absolute);
            if (executable) {
                return new Detection(absolute, Status.READY, sourceOf(absolute, configured), "");
            }
            if (permissionDenied == null) permissionDenied = absolute;
        }
        if (permissionDenied != null) {
            return new Detection(null, Status.PERMISSION_ERROR, "filesystem",
                    "Found " + permissionDenied + " but it is not executable. "
                            + (isWindows() ? "Use the official .exe release."
                            : "Grant execute permission (for example: chmod +x <path>), then retry."));
        }
        return new Detection(null,
                configured == null || configured.isBlank() ? Status.NOT_INSTALLED : Status.MISSING,
                "filesystem",
                configured == null || configured.isBlank()
                        ? "No yt-dlp executable was found in configured, managed, PATH or common user locations."
                        : "The configured executable path does not point to a regular file.");
    }

    private static List<Path> searchDirectories() {
        Set<Path> directories = new LinkedHashSet<>();
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String part : pathEnv.split(java.io.File.pathSeparator)) {
                if (!part.isBlank()) directories.add(Path.of(part));
            }
        }
        String home = System.getProperty("user.home", "");
        if (!home.isBlank()) {
            Path homePath = Path.of(home);
            directories.add(homePath.resolve(".local").resolve("bin"));
            directories.add(homePath.resolve(".cargo").resolve("bin"));
            if (isWindows()) {
                directories.add(homePath.resolve("scoop").resolve("shims"));
            } else if (isMac()) {
                directories.add(Path.of("/opt/homebrew/bin"));
            }
        }
        if (isWindows()) {
            String local = System.getenv("LOCALAPPDATA");
            if (local != null && !local.isBlank()) {
                Path localPath = Path.of(local);
                directories.add(localPath.resolve("Microsoft").resolve("WinGet").resolve("Links"));
                directories.add(localPath.resolve("Programs").resolve("yt-dlp"));
            }
        } else {
            directories.add(Path.of("/usr/local/bin"));
        }
        List<Path> result = new ArrayList<>();
        for (Path directory : directories) {
            try {
                if (Files.isDirectory(directory)) result.add(directory.toAbsolutePath().normalize());
            } catch (RuntimeException ignored) {
                // Malformed environment entries are skipped.
            }
        }
        return result;
    }

    private static List<String> candidateNames() {
        List<String> names = new ArrayList<>();
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (isWindows()) {
            if (arch.contains("aarch64") || arch.contains("arm64")) names.add("yt-dlp_arm64.exe");
            else if (arch.contains("i386") || arch.contains("x86_32") || arch.equals("x86")) {
                names.add("yt-dlp_x86.exe");
            }
            names.add("yt-dlp.exe");
        } else if (isMac()) {
            names.add("yt-dlp_macos");
        } else if (isMusl()) {
            if (arch.contains("aarch64") || arch.contains("arm64")) names.add("yt-dlp_musllinux_aarch64");
            else names.add("yt-dlp_musllinux");
        } else {
            if (arch.contains("aarch64") || arch.contains("arm64")) names.add("yt-dlp_linux_aarch64");
            else if (arch.contains("armv7") || arch.equals("arm")) names.add("yt-dlp_linux_armv7l");
            else if (arch.contains("i386") || arch.contains("x86_32") || arch.equals("x86")) {
                names.add("yt-dlp_x86");
            } else {
                names.add("yt-dlp_linux");
            }
        }
        names.add("yt-dlp");
        if (isWindows()) names.add("yt-dlp.exe");
        return names;
    }

    private static String installAssetName() {
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (isWindows()) {
            if (arch.contains("aarch64") || arch.contains("arm64")) return "yt-dlp_arm64.exe";
            if (arch.contains("i386") || arch.contains("x86_32") || arch.equals("x86")) return "yt-dlp_x86.exe";
            return "yt-dlp.exe";
        }
        if (isMac()) return "yt-dlp_macos";
        if (isMusl()) {
            if (arch.contains("aarch64") || arch.contains("arm64")) return "yt-dlp_musllinux_aarch64";
            return "yt-dlp_musllinux";
        }
        if (arch.contains("aarch64") || arch.contains("arm64")) return "yt-dlp_linux_aarch64";
        if (arch.contains("armv7") || arch.equals("arm")) return null;
        if (arch.contains("i386") || arch.contains("x86_32") || arch.equals("x86")) return "yt-dlp_x86";
        return "yt-dlp_linux";
    }

    private static boolean isMusl() {
        try {
            if (Files.exists(Path.of("/etc/alpine-release"))) return true;
            Path lib = Path.of("/lib");
            if (!Files.isDirectory(lib)) return false;
            try (var entries = Files.list(lib)) {
                return entries.anyMatch(path -> path.getFileName().toString().startsWith("ld-musl-"));
            }
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
    }

    private static boolean looksExecutable(Path path) {
        if (isWindows()) {
            // Official standalone Windows assets are .exe. .cmd/.bat need a shell, which is never used.
            return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".exe");
        }
        return Files.isExecutable(path);
    }

    private static Path safeAbsolute(Path candidate) {
        try {
            return candidate.toAbsolutePath().normalize();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Path resolveUserPath(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        if (value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            value = value.substring(1, value.length() - 1).trim();
        }
        if (value.isEmpty()) return null;
        if (value.equals("~") || value.startsWith("~/") || value.startsWith("~\\")) {
            String home = System.getProperty("user.home", "");
            if (home.isBlank()) return null;
            value = home + value.substring(1);
        }
        try {
            Path path = Path.of(value);
            return path.isAbsolute() ? path.normalize()
                    : FabricLoader.getInstance().getGameDir().resolve(path).normalize();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String sanitized(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("\\p{Cntrl}+", "").trim();
    }

    private static String sourceOf(Path path, String configured) {
        if (configured != null && !configured.isBlank()
                && path.equals(safeAbsolute(resolveUserPath(configured)))) {
            return "configured path";
        }
        try {
            Path managed = dependencyDirectory().toAbsolutePath().normalize();
            if (path.startsWith(managed)) return "mod-managed dependency";
        } catch (RuntimeException ignored) {
            // Fall through to PATH wording.
        }
        return "PATH / user install location";
    }

    private static String parseVersion(String output) {
        if (output == null) return null;
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(20\\d{2})\\.(\\d{2})\\.(\\d{2})").matcher(output);
        return matcher.find() ? matcher.group(0) : null;
    }

    private static int compareDates(String left, String right) {
        return left.compareTo(right);
    }

    private static String checksumFor(byte[] checksumFile, String asset) throws IOException {
        String content = new String(checksumFile, StandardCharsets.UTF_8);
        for (String line : content.split("\\R")) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length >= 2 && parts[1].equals(asset)
                    && parts[0].matches("(?i)[0-9a-f]{64}")) {
                return parts[0].toLowerCase(Locale.ROOT);
            }
        }
        return null;
    }

    private static boolean matches(String expected, byte[] binary) {
        try {
            String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(binary));
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                    actual.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException e) {
            return false;
        }
    }

    private static void publish(Consumer<DownloadProgress> progress, long downloaded, long total,
                                String stage, boolean cancelled) {
        if (progress == null) return;
        try {
            progress.accept(new DownloadProgress(downloaded, total, stage, cancelled));
        } catch (RuntimeException ignored) {
            // Progress reporting must never break the installation.
        }
    }

    private static InstallResult cancelled() {
        return new InstallResult(false, "Installation cancelled. No executable was changed.");
    }

    private static String tail(String value, int max) {
        if (value == null) return "";
        String text = value.trim();
        return text.length() <= max ? text : text.substring(text.length() - max);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static boolean isMac() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("mac") || os.contains("darwin");
    }

    /** Exposed for tests/diagnostics without exposing mutable detection state. */
    static List<String> debugCandidateNames() {
        return List.copyOf(candidateNames());
    }

    /** Kept for callers that need a bounded duration when scheduling follow-up checks. */
    static Duration checkInterval() {
        return Duration.ofNanos(DETECTION_TTL_NANOS);
    }
}
