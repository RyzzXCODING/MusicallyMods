package cc.alpoyy.musicallymods.util;

import cc.alpoyy.musicallymods.config.ModConfig;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
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
        return requestBuilder(url).thenCompose(builder -> {
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

    public static void shutdown() {
        POOL.shutdownNow();
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
