package cc.alpoyy.musicallymods.auth;

import cc.alpoyy.musicallymods.util.Errors;
import cc.alpoyy.musicallymods.util.JsonUtil;
import cc.alpoyy.musicallymods.util.NetworkManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Independent Spotify Web API connection using public-client Authorization Code + PKCE. */
public final class SpotifyConnection extends BaseConnection {
    public static final String AUTHORIZE = "https://accounts.spotify.com/authorize";
    public static final String TOKEN = "https://accounts.spotify.com/api/token";
    public static final String ME = "https://api.spotify.com/v1/me";
    public static final String DEVICES = "https://api.spotify.com/v1/me/player/devices";
    public static final String PLAY = "https://api.spotify.com/v1/me/player/play";
    private static final String SCOPE = "user-read-private user-read-email "
            + "user-read-playback-state user-modify-playback-state";

    /** A Spotify Connect device reported by the official Web API. */
    public record Device(String id, String name, String type, boolean active, boolean restricted) {
        public String label() {
            return name + (restricted ? " (restricted)" : "") + (active ? " [active]" : "");
        }
    }

    private record Profile(String id, String name) {
    }

    private volatile OAuthResult.Pending pending;
    private volatile String lastAuthorizationUrl = "";

    SpotifyConnection() {
        super("spotify", PlatformConnection.State.NOT_CONNECTED,
                "Sign in through Spotify's official authorization page. No password is entered in Minecraft.",
                false);
        AuthStore.Session saved = session();
        if (saved != null) {
            update(saved.expired() ? PlatformConnection.State.TOKEN_EXPIRED
                            : PlatformConnection.State.RECONNECTING,
                    saved.displayName(), "",
                    saved.expired() ? "Saved Spotify session expired; reconnect when ready."
                            : "Saved Spotify session found; validating silently before showing Connected.",
                    true);
        }
    }

    /** Official authorization URL from the last request, shown only if the browser cannot be opened. */
    public String lastAuthorizationUrl() {
        return lastAuthorizationUrl;
    }

    /** True when the user has supplied their own public Spotify Client ID. */
    public boolean configured() {
        return !clientId().isBlank();
    }

    /** Interactive connect; only called after the GUI has obtained explicit user confirmation. */
    public CompletableFuture<Void> connect() {
        if (pending != null) {
            update(PlatformConnection.State.WAITING_FOR_AUTH, "", "",
                    "A Spotify authorization request is already waiting.", false);
            return CompletableFuture.completedFuture(null);
        }
        String clientId = clientId();
        if (clientId.isBlank()) {
            update(PlatformConnection.State.INTEGRATION_ERROR, "", "",
                    "A Spotify Client ID is required. Register an app in the Spotify Developer Dashboard with "
                            + "redirect URI http://127.0.0.1 (loopback IP without a port; Spotify permits dynamic "
                            + "ports), then put the public Client ID in config/musicallymods-auth.json. "
                            + "No client secret is used or embedded.", false);
            return CompletableFuture.completedFuture(null);
        }
        if (!consentGranted()) {
            update(PlatformConnection.State.INTEGRATION_ERROR, "", "", missingConsent(), false);
            return CompletableFuture.completedFuture(null);
        }
        try {
            pending = OAuthClient.begin(AUTHORIZE, clientId, SCOPE, Map.of("show_dialog", "true"));
        } catch (RuntimeException e) {
            update(PlatformConnection.State.INTEGRATION_ERROR, "", Errors.describe(e),
                    "Could not start the official authorization flow.", false);
            return CompletableFuture.completedFuture(null);
        }
        OAuthResult.Pending current = pending;
        lastAuthorizationUrl = current.authorizationUrl();
        boolean opened = OAuthClient.openBrowser(current.authorizationUrl());
        update(PlatformConnection.State.WAITING_FOR_AUTH, "", "",
                opened ? "Approve access in the official Spotify page, then return to Minecraft."
                        : "Browser launch failed. Open this official URL manually: " + current.authorizationUrl(),
                false);
        return current.callback()
                .thenCompose(callback -> OAuthClient.exchange(TOKEN, current, callback, clientId, null))
                .thenCompose(this::store)
                .whenComplete((ignored, throwable) -> {
                    pending = null;
                    if (throwable != null) {
                        update(PlatformConnection.State.AUTHORIZATION_FAILED, "", Errors.describe(throwable),
                                "Spotify authorization could not be completed.", false);
                    }
                });
    }

    /** Silent startup/refresh reconnect. Never opens a browser or deletes saved settings. */
    @Override
    public CompletableFuture<Void> reconnect() {
        AuthStore.Session saved = session();
        if (saved == null) {
            update(PlatformConnection.State.NOT_CONNECTED, "", "", "No Spotify session is saved.", false);
            return CompletableFuture.completedFuture(null);
        }
        update(PlatformConnection.State.RECONNECTING, saved.displayName(), "",
                "Checking the saved Spotify session...", true);
        CompletableFuture<Void> result;
        if (saved.expired() && saved.canRefresh()) {
            result = OAuthClient.refresh(TOKEN, clientId(), "", saved.refreshToken())
                    .thenCompose(token -> store(token));
        } else if (saved.expired()) {
            update(PlatformConnection.State.TOKEN_EXPIRED, saved.displayName(), "",
                    "Saved Spotify session expired; reconnect when ready.", true);
            return CompletableFuture.completedFuture(null);
        } else {
            result = fetchProfile(saved.accessToken(), saved).thenCompose(profile -> {
                if (profile == null) return CompletableFuture.completedFuture(null);
                AuthStore.Session updated = new AuthStore.Session(saved.accessToken(), saved.refreshToken(),
                        saved.expiresAtEpochMs, profile.id(), profile.name(), saved.scopes());
                AuthStore.get().sessions.put("spotify", updated);
                AuthStore.save(AuthStore.get());
                update(PlatformConnection.State.CONNECTED, profile.name(), "",
                        "Saved Spotify session validated. In-game audio is not exposed.", true);
                return CompletableFuture.completedFuture(null);
            });
        }
        return result.exceptionally(throwable -> {
            AuthStore.Session current = session();
            update(PlatformConnection.State.INTEGRATION_ERROR,
                    current == null ? "" : current.displayName(), Errors.describe(throwable),
                    "Spotify reconnect failed. Your saved settings were kept.", current != null);
            return null;
        });
    }

    /** Returns a usable token, refreshing a stale session through the official grant when possible. */
    public CompletableFuture<String> accessToken() {
        AuthStore.Session saved = session();
        if (saved == null) return CompletableFuture.completedFuture("");
        if (!saved.expired()) return CompletableFuture.completedFuture(saved.accessToken());
        if (!saved.canRefresh()) {
            update(PlatformConnection.State.TOKEN_EXPIRED, saved.displayName(), "",
                    "Spotify session expired; reconnect is required.", true);
            return CompletableFuture.completedFuture("");
        }
        return OAuthClient.refresh(TOKEN, clientId(), "", saved.refreshToken())
                .thenCompose(token -> store(token))
                .thenApply(ignored -> {
                    AuthStore.Session current = session();
                    return current == null ? "" : current.accessToken();
                })
                .exceptionally(throwable -> {
                    update(PlatformConnection.State.TOKEN_EXPIRED, saved.displayName(),
                            Errors.describe(throwable), "Spotify refresh failed; reconnect is required.", true);
                    return "";
                });
    }

    /** Lists active Spotify Connect devices through the official Web API. */
    public CompletableFuture<List<Device>> devices() {
        return accessToken().thenCompose(token -> {
            if (token.isBlank()) return CompletableFuture.completedFuture(List.of());
            return NetworkManager.getString(DEVICES, Map.of("Authorization", "Bearer " + token))
                    .handle((body, throwable) -> {
                        if (throwable != null) {
                            update(PlatformConnection.State.INTEGRATION_ERROR, "", Errors.describe(throwable),
                                    "Spotify device list failed. Check that a device is active and unrestricted.", true);
                            return List.<Device>of();
                        }
                        List<Device> result = new ArrayList<>();
                        try {
                            JsonObject root = JsonUtil.object(JsonParser.parseString(body));
                            JsonArray array = root == null ? null : root.getAsJsonArray("devices");
                            if (array != null) {
                                for (JsonElement element : array) {
                                    JsonObject item = JsonUtil.object(element);
                                    if (item == null) continue;
                                    String id = JsonUtil.string(item, "id");
                                    if (id == null) continue;
                                    result.add(new Device(id,
                                            value(item, "name", "Unnamed device"),
                                            value(item, "type", "Computer"),
                                            bool(item, "is_active"), bool(item, "is_restricted")));
                                }
                            }
                        } catch (RuntimeException e) {
                            update(PlatformConnection.State.INTEGRATION_ERROR, "", Errors.describe(e),
                                    "Spotify returned an unreadable device list.", true);
                            return List.<Device>of();
                        }
                        return List.copyOf(result);
                    });
        });
    }

    /** Starts a track on a Spotify Connect device. This never streams audio into Minecraft. */
    public CompletableFuture<String> play(Device device, String spotifyUri) {
        if (device == null || spotifyUri == null || !spotifyUri.startsWith("spotify:track:")) {
            return CompletableFuture.completedFuture("Select a device and a Spotify track first.");
        }
        if (device.restricted()) {
            return CompletableFuture.completedFuture("That device is restricted and cannot be controlled by Web API.");
        }
        return accessToken().thenCompose(token -> {
            if (token.isBlank()) return CompletableFuture.completedFuture("Connect to Spotify first.");
            String body = "{\"uris\":[\"" + spotifyUri + "\"]}";
            String endpoint = PLAY + "?device_id=" + encode(device.id());
            return NetworkManager.request("PUT", endpoint,
                    Map.of("Authorization", "Bearer " + token, "Content-Type", "application/json"), body)
                    .handle((ignored, throwable) -> throwable == null
                            ? "Sent " + spotifyUri + " to " + device.name() + "."
                            : "Spotify Connect control failed: " + Errors.describe(throwable));
        });
    }

    @Override
    public void cancel() {
        OAuthResult.Pending current = pending;
        if (current != null) current.cancel().run();
        pending = null;
    }

    private CompletableFuture<Void> store(OAuthResult.Token token) {
        if (!token.valid()) {
            boolean saved = session() != null;
            update(PlatformConnection.State.AUTHORIZATION_FAILED, "", token.error(),
                    "Spotify did not return a usable session. Saved settings were kept.", saved);
            return CompletableFuture.completedFuture(null);
        }
        return fetchProfile(token.accessToken(), null).thenCompose(profile -> {
            if (profile == null) return CompletableFuture.completedFuture(null);
            AuthStore.Session current = session();
            long expires = System.currentTimeMillis() + token.expiresInSeconds() * 1000L;
            String refresh = token.refreshToken().isBlank()
                    ? (current == null ? "" : current.refreshToken()) : token.refreshToken();
            AuthStore.Session saved = new AuthStore.Session(token.accessToken(), refresh,
                    expires, profile.id(), profile.name(), token.scope());
            AuthStore.get().sessions.put("spotify", saved);
            AuthStore.save(AuthStore.get());
            update(PlatformConnection.State.CONNECTED, saved.displayName(), "",
                    "Connected to Spotify through the official Web API. In-game audio is not exposed.", true);
            return CompletableFuture.completedFuture(null);
        });
    }

    private CompletableFuture<Profile> fetchProfile(String token, AuthStore.Session previous) {
        return NetworkManager.getString(ME, Map.of("Authorization", "Bearer " + token))
                .handle((body, throwable) -> {
                    AuthStore.Session saved = previous == null ? session() : previous;
                    String account = saved == null ? "" : saved.displayName();
                    boolean hasSession = saved != null;
                    if (throwable != null) {
                        String message = Errors.describe(throwable);
                        if (message.contains("HTTP 401")) {
                            update(PlatformConnection.State.TOKEN_EXPIRED, account, "",
                                    "Spotify rejected the session; reconnect is required.", hasSession);
                        } else {
                            update(PlatformConnection.State.INTEGRATION_ERROR, account, message,
                                    "Spotify profile validation failed. Saved settings were kept.", hasSession);
                        }
                        return null;
                    }
                    try {
                        JsonObject object = JsonUtil.object(JsonParser.parseString(body));
                        String id = object == null ? null : JsonUtil.string(object, "id");
                        String name = object == null ? null : JsonUtil.string(object, "display_name");
                        if (id == null && name == null) {
                            update(PlatformConnection.State.INTEGRATION_ERROR, account, "",
                                    "Spotify returned an unreadable profile. Saved settings were kept.", hasSession);
                            return null;
                        }
                        return new Profile(id == null ? "" : id, name == null ? account : name);
                    } catch (RuntimeException e) {
                        update(PlatformConnection.State.INTEGRATION_ERROR, account, Errors.describe(e),
                                "Spotify returned an unreadable profile.", hasSession);
                        return null;
                    }
                });
    }

    private static String value(JsonObject object, String key, String fallback) {
        String value = JsonUtil.string(object, key);
        return value == null ? fallback : value;
    }

    private static boolean bool(JsonObject object, String key) {
        try {
            JsonElement element = object.get(key);
            return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()
                    && element.getAsBoolean();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private String clientId() {
        String value = AuthStore.get().spotifyClientId;
        return value == null ? "" : value.trim();
    }
}
