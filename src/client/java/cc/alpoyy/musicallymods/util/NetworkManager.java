package cc.alpoyy.musicallymods.util;

import cc.alpoyy.musicallymods.config.ModConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/** Shared asynchronous HTTP client. No request is ever issued on the render thread. */
public final class NetworkManager {
    public static final String USER_AGENT = "MusicallyMods/0.2 (+https://github.com/RyzzXCODING/MusicallyMods)";
    private static final long MAX_JSON_BYTES = 2L * 1024L * 1024L;
    private static final long MAX_BINARY_BYTES = 8L * 1024L * 1024L;
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
            return CLIENT.sendAsync(request,
                            HttpResponse.BodyHandlers.limiting(
                                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8), MAX_JSON_BYTES))
                    .thenCompose(response -> checkStatus(response.statusCode(), url)
                            ? CompletableFuture.completedFuture(response.body())
                            : CompletableFuture.<String>failedFuture(httpError(response.statusCode(), url)));
        });
    }

    public static CompletableFuture<byte[]> getBytes(String url) {
        return requestBuilder(url).thenCompose(builder -> {
            HttpRequest request = builder.header("Accept", "image/*, application/octet-stream").build();
            return CLIENT.sendAsync(request,
                            HttpResponse.BodyHandlers.limiting(
                                    HttpResponse.BodyHandlers.ofByteArray(), MAX_BINARY_BYTES))
                    .thenCompose(response -> checkStatus(response.statusCode(), url)
                            ? CompletableFuture.completedFuture(response.body())
                            : CompletableFuture.<byte[]>failedFuture(httpError(response.statusCode(), url)));
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

    private static boolean checkStatus(int status, String url) {
        return status >= 200 && status < 300;
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
