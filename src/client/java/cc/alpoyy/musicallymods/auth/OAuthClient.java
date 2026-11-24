package cc.alpoyy.musicallymods.auth;

import cc.alpoyy.musicallymods.util.Errors;
import cc.alpoyy.musicallymods.util.JsonUtil;
import cc.alpoyy.musicallymods.util.NetworkManager;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Official OAuth 2.0 authorization-code + PKCE helper with a loopback-only callback. */
public final class OAuthClient {
    private static final int CALLBACK_TIMEOUT_MS = 5 * 60 * 1000;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "musicallymods-oauth");
        thread.setDaemon(true);
        return thread;
    });

    private OAuthClient() {
    }

    /** Creates a local loopback listener on an ephemeral port before returning the authorization URL. */
    public static OAuthResult.Pending begin(String authorizationEndpoint, String clientId, String scope,
                                 Map<String, String> extraParameters) {
        return begin(authorizationEndpoint, clientId, scope, extraParameters, 0);
    }

    /**
     * Creates a loopback listener on a specific port. Providers whose registration requires an exact
     * redirect URI (such as SoundCloud desktop apps) can use a fixed, registered loopback port.
     */
    public static OAuthResult.Pending begin(String authorizationEndpoint, String clientId, String scope,
                                Map<String, String> extraParameters, int port) {
        if (port < 0 || port > 65535) {
            throw new IllegalStateException("The configured callback port is outside the valid range.");
        }
        String verifier = randomToken(32);
        String challenge = challenge(verifier);
        String state = randomToken(16);
        ServerSocket server = null;
        try {
            // Bind exactly the IPv4 address advertised in redirect_uri; getLoopbackAddress() can be ::1.
            server = new ServerSocket(port, 1, InetAddress.getByName("127.0.0.1"));
        } catch (IOException e) {
            throw new IllegalStateException("Could not open a local callback port: " + Errors.describe(e));
        }
        ServerSocket listener = server;
        String redirect = "http://127.0.0.1:" + listener.getLocalPort();
        Map<String, String> parameters = new LinkedHashMap<>();
        if (extraParameters != null) parameters.putAll(extraParameters);
        if (scope != null && !scope.isBlank()) parameters.put("scope", scope);
        // Security-critical values are set last so no caller-supplied parameter can override them.
        parameters.put("response_type", "code");
        parameters.put("client_id", clientId);
        parameters.put("redirect_uri", redirect);
        parameters.put("code_challenge", challenge);
        parameters.put("code_challenge_method", "S256");
        parameters.put("state", state);
        String url = authorizationEndpoint + (authorizationEndpoint.contains("?") ? "&" : "?")
                + encode(parameters);

        CompletableFuture<OAuthResult.Callback> callback = CompletableFuture.supplyAsync(
                () -> listen(listener, state, redirect), WORKER);
        return new OAuthResult.Pending(url, redirect, verifier, callback, () -> {
            try {
                listener.close();
            } catch (IOException ignored) {
                // Already closed after a callback.
            }
            callback.cancel(true);
        });
    }

    /** Exchanges an authorization code. A public-client secret is never invented or embedded. */
    public static CompletableFuture<OAuthResult.Token> exchange(String tokenEndpoint,
                                                                 OAuthResult.Pending pending,
                                                                 OAuthResult.Callback callback, String clientId,
                                                     String clientSecret) {
        if (callback == null || !callback.valid()) {
            return CompletableFuture.completedFuture(
                    new OAuthResult.Token(null, null, 0, null, "Authorization was not completed."));
        }
        if (!pending.state().equals(callback.state())) {
            return CompletableFuture.completedFuture(
                    new OAuthResult.Token(null, null, 0, null, "Authorization state did not match; request rejected."));
        }
        Map<String, String> form = new HashMap<>();
        form.put("grant_type", "authorization_code");
        form.put("code", callback.code());
        form.put("redirect_uri", pending.redirectUri());
        form.put("client_id", clientId);
        form.put("code_verifier", pending.codeVerifier());
        if (clientSecret != null && !clientSecret.isBlank()) form.put("client_secret", clientSecret);
        return parseToken(NetworkManager.postForm(tokenEndpoint, form, null));
    }

    /** Refreshes a session using the provider's supported refresh-token grant. */
    public static CompletableFuture<OAuthResult.Token> refresh(String tokenEndpoint, String clientId,
                                                    String clientSecret, String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return CompletableFuture.completedFuture(
                    new OAuthResult.Token(null, null, 0, null, "No refresh token is available."));
        }
        Map<String, String> form = new HashMap<>();
        form.put("grant_type", "refresh_token");
        form.put("refresh_token", refreshToken);
        form.put("client_id", clientId);
        if (clientSecret != null && !clientSecret.isBlank()) form.put("client_secret", clientSecret);
        return parseToken(NetworkManager.postForm(tokenEndpoint, form, null));
    }

    /** Opens the official page if the desktop supports it; the caller shows the URL on failure. */
    public static boolean openBrowser(String url) {
        try {
            if (!Desktop.isDesktopSupported()) return false;
            Desktop desktop = Desktop.getDesktop();
            if (!desktop.isSupported(Desktop.Action.BROWSE)) return false;
            desktop.browse(URI.create(url));
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static void shutdown() {
        WORKER.shutdownNow();
    }

    private static CompletableFuture<OAuthResult.Token> parseToken(CompletableFuture<String> response) {
        return response.handle((body, throwable) -> {
            if (throwable != null) {
                return new OAuthResult.Token(null, null, 0, null,
                        "Token request failed: " + Errors.describe(throwable));
            }
            try {
                JsonElement parsed = JsonParser.parseString(body == null ? "" : body);
                JsonObject object = JsonUtil.object(parsed);
                if (object == null) {
                    return new OAuthResult.Token(null, null, 0, null, "The authorization server returned invalid JSON.");
                }
                String error = JsonUtil.string(object, "error");
                if (error != null) {
                    String description = JsonUtil.string(object, "error_description");
                    return new OAuthResult.Token(null, null, 0, null,
                            Errors.sanitize(error + (description == null ? "" : ": " + description)));
                }
                String access = JsonUtil.string(object, "access_token");
                String refresh = JsonUtil.string(object, "refresh_token");
                long expires = JsonUtil.number(object, "expires_in", 0L);
                String scope = JsonUtil.string(object, "scope");
                if (access == null || expires <= 0) {
                    return new OAuthResult.Token(null, null, 0, null,
                            "The authorization server did not return a usable token response.");
                }
                return new OAuthResult.Token(access, refresh == null ? "" : refresh, expires, scope == null ? "" : scope, null);
            } catch (RuntimeException e) {
                return new OAuthResult.Token(null, null, 0, null,
                        "The authorization server returned an unreadable response: " + Errors.describe(e));
            }
        });
    }

    private static OAuthResult.Callback listen(ServerSocket server, String expectedState, String redirect) {
        try {
            server.setSoTimeout(CALLBACK_TIMEOUT_MS);
            try (Socket socket = server.accept()) {
                socket.setSoTimeout(5_000);
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                String request = reader.readLine();
                Map<String, String> query = parseRequest(request);
                String state = query.get("state");
                String error = query.get("error");
                String code = query.get("code");
                String html = "<html><body><h3>MusicallyMods</h3><p>"
                        + (error == null ? "Authorization complete. You can close this tab."
                        : "Authorization was declined or failed. You can close this tab.")
                        + "</p></body></html>";
                byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
                try (OutputStream out = socket.getOutputStream()) {
                    out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=UTF-8\r\n"
                            + "Content-Length: " + bytes.length + "\r\nConnection: close\r\n\r\n")
                            .getBytes(StandardCharsets.US_ASCII));
                    out.write(bytes);
                }
                if (error != null) {
                    return new OAuthResult.Callback(null, null, Errors.sanitize(error), redirect);
                }
                if (state == null || !expectedState.equals(state)) {
                    return new OAuthResult.Callback(null, null, "Authorization state did not match; request rejected.", redirect);
                }
                return new OAuthResult.Callback(code, state, null, redirect);
            }
        } catch (SocketTimeoutException e) {
            return new OAuthResult.Callback(null, null, "Authorization timed out before the browser responded.", redirect);
        } catch (IOException e) {
            return new OAuthResult.Callback(null, null, "Local callback error: " + Errors.describe(e), redirect);
        } finally {
            try {
                server.close();
            } catch (IOException ignored) {
                // Best-effort listener cleanup.
            }
        }
    }

    private static Map<String, String> parseRequest(String request) {
        Map<String, String> values = new HashMap<>();
        if (request == null || request.isBlank()) return values;
        String[] parts = request.split(" ");
        if (parts.length < 2) return values;
        int question = parts[1].indexOf('?');
        if (question < 0 || question == parts[1].length() - 1) return values;
        for (String pair : parts[1].substring(question + 1).split("&")) {
            int equals = pair.indexOf('=');
            if (equals <= 0) continue;
            try {
                values.put(URLDecoder.decode(pair.substring(0, equals), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8));
            } catch (RuntimeException ignored) {
                // A malformed callback parameter is ignored and fails validation.
            }
        }
        return values;
    }

    private static String randomToken(int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static String challenge(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required for PKCE.", e);
        }
    }

    private static String encode(Map<String, String> parameters) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            if (builder.length() > 0) builder.append('&');
            builder.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)).append('=')
                    .append(URLEncoder.encode(entry.getValue() == null ? "" : entry.getValue(),
                            StandardCharsets.UTF_8));
        }
        return builder.toString();
    }
}
