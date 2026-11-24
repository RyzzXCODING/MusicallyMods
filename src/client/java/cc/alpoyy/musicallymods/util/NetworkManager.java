package cc.alpoyy.musicallymods.util;

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
    private static final ExecutorService POOL = Executors.newFixedThreadPool(4, daemonFactory("musicallymods-http"));
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .executor(POOL)
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private NetworkManager() {
    }

    public static CompletableFuture<String> getString(String url) {
        HttpRequest request = requestBuilder(url).header("Accept", "application/json").build();
        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenCompose(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        return CompletableFuture.failedFuture(
                                new IOException("HTTP " + response.statusCode() + " from " + hostOf(url)));
                    }
                    return CompletableFuture.completedFuture(response.body());
                });
    }

    public static CompletableFuture<byte[]> getBytes(String url) {
        HttpRequest request = requestBuilder(url).header("Accept", "image/*, application/octet-stream").build();
        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .thenCompose(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        return CompletableFuture.failedFuture(
                                new IOException("HTTP " + response.statusCode() + " from " + hostOf(url)));
                    }
                    return CompletableFuture.completedFuture(response.body());
                });
    }

    public static void shutdown() {
        POOL.shutdownNow();
    }

    private static HttpRequest.Builder requestBuilder(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", USER_AGENT)
                .GET();
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
