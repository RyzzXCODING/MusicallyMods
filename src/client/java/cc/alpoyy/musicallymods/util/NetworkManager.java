package cc.alpoyy.musicallymods.util;

import cc.alpoyy.musicallymods.config.ModConfig;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.Map;

/** Shared asynchronous HTTP client. No request or body read is issued on the render thread. */
public final class NetworkManager {
    public static final String USER_AGENT = "MusicallyMods/0.2 (+https://github.com/RyzzXCODING/MusicallyMods)";
    private static final long MAX_JSON_BYTES = 2L * 1024L * 1024L;
    private static final long MAX_BINARY_BYTES = 8L * 1024L * 1024L;
    private static final int READ_BUFFER_BYTES = 8192;
    private static final ExecutorService POOL = Executors.newFixedThreadPool(4, daemonFactory("musicallymods-http"));
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .executor(POOL)
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private NetworkManager() {
    }

    public static CompletableFuture<String> getString(String url) {
        return getString(url, null);
    }

    public static CompletableFuture<String> getString(String url, Map<String, String> headers) {
        return requestBuilder(url).thenCompose(builder -> {
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    builder.header(entry.getKey(), entry.getValue());
                }
            }
            HttpRequest request = builder.header("Accept", "application/json").build();
            return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                    .thenApplyAsync(response -> {
                        requireSuccess(response, url);
                        return new String(readBounded(response.body(), MAX_JSON_BYTES), StandardCharsets.UTF_8);
                    }, POOL);
        });
    }

    public static CompletableFuture<byte[]> getBytes(String url) {
        return requestBuilder(url).thenCompose(builder -> {
            HttpRequest request = builder.header("Accept", "image/*, application/octet-stream").build();
            return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                    .thenApplyAsync(response -> {
                        requireSuccess(response, url);
                        return readBounded(response.body(), MAX_BINARY_BYTES);
                    }, POOL);
        });
    }

    /**
     * Sends a urlencoded POST without exposing form values in logs. Used only by official OAuth
     * token endpoints; callers must redact any secrets before displaying errors.
     */
    public static CompletableFuture<String> postForm(String url, Map<String, String> form,
                                                      Map<String, String> headers) {
        return CompletableFuture.supplyAsync(() -> {
            HttpURLConnection connection = null;
            try {
                StringBuilder body = new StringBuilder();
                if (form != null) {
                    for (Map.Entry<String, String> entry : form.entrySet()) {
                        if (body.length() > 0) body.append('&');
                        body.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                                .append('=')
                                .append(URLEncoder.encode(entry.getValue() == null ? "" : entry.getValue(),
                                        StandardCharsets.UTF_8));
                    }
                }
                byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setConnectTimeout(8_000);
                connection.setReadTimeout(timeoutMillis());
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", USER_AGENT);
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Content-Length", Integer.toString(payload.length));
                if (headers != null) {
                    for (Map.Entry<String, String> entry : headers.entrySet()) {
                        connection.setRequestProperty(entry.getKey(), entry.getValue());
                    }
                }
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(payload);
                }
                int status = connection.getResponseCode();
                InputStream stream = status >= 200 && status < 300
                        ? connection.getInputStream() : connection.getErrorStream();
                byte[] response = stream == null ? new byte[0]
                        : readBounded(stream, MAX_JSON_BYTES);
                if (status < 200 || status >= 300) {
                    throw new IOException("OAuth endpoint returned HTTP " + status);
                }
                return new String(response, StandardCharsets.UTF_8);
            } catch (IOException | RuntimeException e) {
                throw new CompletionException(e);
            } finally {
                if (connection != null) connection.disconnect();
            }
        }, POOL);
    }

    /** Sends an authenticated JSON request to an official API with a bounded response read. */
    public static CompletableFuture<String> request(String method, String url,
                                                     Map<String, String> headers, String body) {
        return CompletableFuture.supplyAsync(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setConnectTimeout(8_000);
                connection.setReadTimeout(timeoutMillis());
                connection.setInstanceFollowRedirects(true);
                connection.setRequestMethod(method);
                connection.setRequestProperty("User-Agent", USER_AGENT);
                connection.setRequestProperty("Accept", "application/json");
                if (headers != null) {
                    for (Map.Entry<String, String> entry : headers.entrySet()) {
                        connection.setRequestProperty(entry.getKey(), entry.getValue());
                    }
                }
                if (body != null) {
                    byte[] payload = body.getBytes(StandardCharsets.UTF_8);
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                    connection.setRequestProperty("Content-Length", Integer.toString(payload.length));
                    try (OutputStream out = connection.getOutputStream()) {
                        out.write(payload);
                    }
                }
                int status = connection.getResponseCode();
                InputStream stream = status >= 200 && status < 300
                        ? connection.getInputStream() : connection.getErrorStream();
                byte[] response = stream == null ? new byte[0] : readBounded(stream, MAX_JSON_BYTES);
                if (status < 200 || status >= 300) throw new IOException("HTTP " + status + " from " + hostOf(url));
                return new String(response, StandardCharsets.UTF_8);
            } catch (IOException | RuntimeException e) {
                throw new CompletionException(e);
            } finally {
                if (connection != null) connection.disconnect();
            }
        }, POOL);
    }

    /** Bounded, cancellable binary download with byte progress; used for verified opt-in installs. */
    public static CompletableFuture<byte[]> download(String url, long maxBytes, AtomicBoolean cancel,
                                                     Consumer<Long> progress) {
        return CompletableFuture.supplyAsync(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setConnectTimeout(8_000);
                connection.setReadTimeout(timeoutMillis());
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", USER_AGENT);
                connection.setRequestProperty("Accept", "application/octet-stream, text/plain");
                int status = connection.getResponseCode();
                if (status < 200 || status >= 300) {
                    throw new IOException("Download endpoint returned HTTP " + status);
                }
                long expected = connection.getContentLengthLong();
                long limit = expected > 0 ? Math.min(maxBytes, expected) : maxBytes;
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[READ_BUFFER_BYTES];
                long total = 0L;
                try (InputStream in = connection.getInputStream()) {
                    while (total < limit) {
                        if (cancel != null && cancel.get()) throw new IOException("cancelled");
                        int wanted = (int) Math.min(buffer.length, limit - total);
                        int read = in.read(buffer, 0, wanted);
                        if (read < 0) break;
                        out.write(buffer, 0, read);
                        total += read;
                        if (progress != null) {
                            try {
                                progress.accept(total);
                            } catch (RuntimeException ignored) {
                                // Progress reporting must not fail the download.
                            }
                        }
                    }
                }
                if (total >= limit && (expected < 0 || expected > maxBytes)) {
                    throw new IOException("Download exceeds the allowed size");
                }
                return out.toByteArray();
            } catch (IOException | RuntimeException e) {
                throw new CompletionException(e);
            } finally {
                if (connection != null) connection.disconnect();
            }
        }, POOL);
    }

    public static void shutdown() {
        POOL.shutdownNow();
    }

    private static int timeoutMillis() {
        int configured = ModConfig.get().playback.requestTimeoutSeconds;
        return Math.max(5_000, Math.min(120_000, configured * 1_000));
    }

    private static CompletableFuture<HttpRequest.Builder> requestBuilder(String url) {
        try {
            int timeout = Math.max(5, Math.min(120, ModConfig.get().playback.requestTimeoutSeconds));
            return CompletableFuture.completedFuture(HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(timeout))
                    .header("User-Agent", USER_AGENT)
                    .GET());
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(new IOException("Invalid service URL"));
        }
    }

    private static void requireSuccess(HttpResponse<InputStream> response, String url) {
        int status = response.statusCode();
        if (status < 200 || status >= 300) throw new CompletionException(httpError(status, url));
    }

    /**
     * Reads at most {@code maxBytes} from a response body. Java 21 has no limiting BodyHandler, so
     * the cap is applied on the HTTP executor while the stream is still open.
     */
    private static byte[] readBounded(InputStream body, long maxBytes) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[READ_BUFFER_BYTES];
        long total = 0L;
        try (InputStream in = body) {
            while (total < maxBytes) {
                int wanted = (int) Math.min(buffer.length, maxBytes - total);
                int read = in.read(buffer, 0, wanted);
                if (read < 0) break;
                out.write(buffer, 0, read);
                total += read;
            }
            if (total >= maxBytes && in.available() > 0) {
                throw new IOException("Response body exceeds " + maxBytes + " bytes");
            }
        } catch (IOException e) {
            throw new CompletionException(e);
        }
        return out.toByteArray();
    }

    private static IOException httpError(int status, String url) {
        return new IOException("HTTP " + status + " from " + hostOf(url));
    }

    private static String hostOf(String url) {
        try {
            return URI.create(url).getHost();
        } catch (RuntimeException e) {
            return "service";
        }
    }

    private static ThreadFactory daemonFactory(String name) {
        return runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        };
    }
}
