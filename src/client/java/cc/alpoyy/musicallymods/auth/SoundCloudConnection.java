package cc.alpoyy.musicallymods.auth;

import cc.alpoyy.musicallymods.util.Errors;
import cc.alpoyy.musicallymods.util.JsonUtil;
import cc.alpoyy.musicallymods.util.NetworkManager;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * SoundCloud connection that uses only the current official developer API.
 *
 * <p>SoundCloud treats every client as confidential, so this page requires the user's own registered
 * Client ID and Client Secret and an Artist Pro subscription. Nothing is embedded and nothing is
 * pretended when those prerequisites are absent.</p>
 */
public final class SoundCloudConnection extends BaseConnection {
    public static final String AUTHORIZE = "https://secure.soundcloud.com/authorize";
    public static final String TOKEN = "https://secure.soundcloud.com/oauth/token";
    public static final String ME = "https://api.soundcloud.com/me";
    public static final String DISCONNECT = "https://api.soundcloud.com/disconnect";
    /** SoundCloud requires an exact registered redirect URI, so this desktop flow uses a fixed loopback port. */
    public static final int REDIRECT_PORT = 8080;
    private static final String REQUIREMENT =
            "SoundCloud's current API requires a registered application and a Client ID plus Client Secret. "
                    + "An Artist Pro subscription is required to obtain them. Register the redirect URI "
                    + "http://127.0.0.1:" + REDIRECT_PORT + ", then put your own credentials in "
                    + "config/musicallymods-auth.json; none are bundled. OAuth 2.1 with PKCE is used and all "
                    + "clients are confidential.";

    private volatile OAuthResult.Pending pending;
    private volatile String lastAuthorizationUrl = "";

    SoundCloudConnection() {
        super("soundcloud", PlatformConnection.State.NOT_CONNECTED,
                REQUIREMENT, true);
        AuthStore.Session saved = session();
        if (saved != null) {
            update(saved.expired() ? PlatformConnection.State.TOKEN_EXPIRED
                            : PlatformConnection.State.RECONNECTING,
                    saved.displayName(), "",
                    saved.expired() ? "Saved SoundCloud session expired; reconnect when ready."
                            : "Saved SoundCloud session found; validating silently before showing Connected.",
                    true);
        }
    }

    /** Official authorization URL from the last request, shown only if the browser cannot be opened. */
    public String lastAuthorizationUrl() {
        return lastAuthorizationUrl;
    }

    /** True when the user has supplied both official confidential-client credentials. */
    public boolean configured() {
        return !clientId().isBlank() && !clientSecret().isBlank();
    }

    /** Interactive connect; only called after the GUI has obtained explicit user confirmation. */
    public CompletableFuture<Void> connect() {
        if (pending != null) {
            update(PlatformConnection.State.WAITING_FOR_AUTH, "", "",
                    "A SoundCloud authorization request is already waiting.", false);
            return CompletableFuture.completedFuture(null);
        }
        if (!configured()) {
            update(PlatformConnection.State.INTEGRATION_ERROR, "", "", REQUIREMENT, false);
            return CompletableFuture.completedFuture(null);
        }
        if (!consentGranted()) {
            update(PlatformConnection.State.INTEGRATION_ERROR, "", "", missingConsent(), false);
            return CompletableFuture.completedFuture(null);
        }
        try {
            pending = OAuthClient.begin(AUTHORIZE, clientId(), "", null, REDIRECT_PORT);
        } catch (RuntimeException e) {
            update(PlatformConnection.State.INTEGRATION_ERROR, "", Errors.describe(e),
                    "Could not start the official SoundCloud authorization flow.", false);
            return CompletableFuture.completedFuture(null);
        }
        OAuthResult.Pending current = pending;
        lastAuthorizationUrl = current.authorizationUrl();
        boolean opened = OAuthClient.openBrowser(current.authorizationUrl());
        update(PlatformConnection.State.WAITING_FOR_AUTH, "", "",
                opened ? "Approve access in the official SoundCloud page, then return to Minecraft."
                        : "Browser launch failed. Open this official URL manually: " + current.authorizationUrl(),
                false);
        return current.callback()
                .thenCompose(callback -> OAuthClient.exchange(TOKEN, current, callback,
                        clientId(), clientSecret()))
                .thenCompose(this::store)
                .whenComplete((ignored, throwable) -> {
                    pending = null;
                    if (throwable != null) {
                        update(PlatformConnection.State.AUTHORIZATION_FAILED, "", Errors.describe(throwable),
                                "SoundCloud authorization could not be completed.", false);
                    }
                });
    }

    /** Silent startup/refresh reconnect. Never opens a browser or deletes saved settings. */
    @Override
    public CompletableFuture<Void> reconnect() {
        AuthStore.Session saved = session();
        if (saved == null) {
            update(configured() ? PlatformConnection.State.NOT_CONNECTED
                            : PlatformConnection.State.PLAYBACK_UNAVAILABLE,
                    "", "", configured() ? "No SoundCloud session is saved." : REQUIREMENT, false);
            return CompletableFuture.completedFuture(null);
        }
        update(PlatformConnection.State.RECONNECTING, saved.displayName(), "",
                "Checking the saved SoundCloud session...", true);
        CompletableFuture<Void> result;
        if (saved.expired() && saved.canRefresh()) {
            result = OAuthClient.refresh(TOKEN, clientId(), clientSecret(), saved.refreshToken())
                    .thenCompose(token -> store(token));
        } else if (saved.expired()) {
            update(PlatformConnection.State.TOKEN_EXPIRED, saved.displayName(), "",
                    "Saved SoundCloud session expired; reconnect when ready.", true);
            return CompletableFuture.completedFuture(null);
        } else {
            result = fetchProfile(saved.accessToken(), saved).thenCompose(profile -> {
                if (profile == null) return CompletableFuture.completedFuture(null);
                AuthStore.Session updated = new AuthStore.Session(saved.accessToken(), saved.refreshToken(),
                        saved.expiresAtEpochMs, profile.id(), profile.name(), saved.scopes());
                AuthStore.get().sessions.put("soundcloud", updated);
                AuthStore.save(AuthStore.get());
                update(PlatformConnection.State.CONNECTED, profile.name(), "",
                        "Saved SoundCloud session validated. Only playable public tracks are offered.", true);
                return CompletableFuture.completedFuture(null);
            });
        }
        return result.exceptionally(throwable -> {
            AuthStore.Session current = session();
            update(PlatformConnection.State.INTEGRATION_ERROR,
                    current == null ? "" : current.displayName(), Errors.describe(throwable),
                    "SoundCloud reconnect failed. Your saved settings were kept.", current != null);
            return null;
        });
    }

    /** Marks a saved session as expired after an official API 401, without deleting it. */
    public void expire() {
        AuthStore.Session saved = session();
        update(PlatformConnection.State.TOKEN_EXPIRED, saved == null ? "" : saved.displayName(), "",
                "SoundCloud rejected the session; reconnect when ready.", saved != null);
    }

    /** Returns a usable token without ever prompting the user. */
    public CompletableFuture<String> accessToken() {
        AuthStore.Session saved = session();
        if (saved == null) return CompletableFuture.completedFuture("");
        if (!saved.expired()) return CompletableFuture.completedFuture(saved.accessToken());
        if (!saved.canRefresh()) {
            update(PlatformConnection.State.TOKEN_EXPIRED, saved.displayName(), "",
                    "SoundCloud session expired; reconnect is required.", true);
            return CompletableFuture.completedFuture("");
        }
        return OAuthClient.refresh(TOKEN, clientId(), clientSecret(), saved.refreshToken())
                .thenCompose(token -> store(token))
                .thenApply(ignored -> {
                    AuthStore.Session current = session();
                    return current == null ? "" : current.accessToken();
                })
                .exceptionally(throwable -> {
                    update(PlatformConnection.State.TOKEN_EXPIRED, saved.displayName(),
                            Errors.describe(throwable), "SoundCloud refresh failed; reconnect is required.", true);
                    return "";
                });
    }

    /** Revokes this app's official OAuth grant before clearing only the local SoundCloud session. */
    @Override
    public CompletableFuture<Void> disconnect() {
        AuthStore.Session saved = session();
        CompletableFuture<Void> revoke = saved == null || saved.accessToken().isBlank()
                ? CompletableFuture.completedFuture(null)
                : NetworkManager.request("POST", DISCONNECT,
                        Map.of("Authorization", "OAuth " + saved.accessToken()), null)
                        .handle((body, throwable) -> null);
        return revoke.thenCompose(ignored -> super.disconnect());
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
                    "SoundCloud did not return a usable session. Saved settings were kept.", saved);
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
            AuthStore.get().sessions.put("soundcloud", saved);
            AuthStore.save(AuthStore.get());
            update(PlatformConnection.State.CONNECTED, saved.displayName(), "",
                    "Connected through the official SoundCloud API. No Go-only or blocked tracks are offered.", true);
            return CompletableFuture.completedFuture(null);
        });
    }

    private CompletableFuture<Profile> fetchProfile(String token, AuthStore.Session previous) {
        return NetworkManager.getString(ME, Map.of("Authorization", "OAuth " + token))
                .handle((body, throwable) -> {
                    AuthStore.Session saved = previous == null ? session() : previous;
                    String account = saved == null ? "" : saved.displayName();
                    boolean hasSession = saved != null;
                    if (throwable != null) {
                        String message = Errors.describe(throwable);
                        PlatformConnection.State state = message.contains("HTTP 401")
                                ? PlatformConnection.State.TOKEN_EXPIRED
                                : PlatformConnection.State.INTEGRATION_ERROR;
                        update(state, account, message,
                                "SoundCloud profile validation failed. Saved settings were kept.", hasSession);
                        return null;
                    }
                    try {
                        JsonObject object = JsonUtil.object(JsonParser.parseString(body));
                        String id = object == null ? null : JsonUtil.string(object, "id");
                        String username = object == null ? null : JsonUtil.string(object, "username");
                        if (id == null && username == null) {
                            update(PlatformConnection.State.INTEGRATION_ERROR, account, "",
                                    "SoundCloud returned an unreadable profile. Saved settings were kept.", hasSession);
                            return null;
                        }
                        return new Profile(id == null ? "" : id, username == null ? account : username);
                    } catch (RuntimeException e) {
                        update(PlatformConnection.State.INTEGRATION_ERROR, account, Errors.describe(e),
                                "SoundCloud returned an unreadable profile.", hasSession);
                        return null;
                    }
                });
    }

    private record Profile(String id, String name) {
    }

    private String clientId() {
        String value = AuthStore.get().soundCloudClientId;
        return value == null ? "" : value.trim();
    }

    private String clientSecret() {
        String value = AuthStore.get().soundCloudClientSecret;
        return value == null ? "" : value.trim();
    }
}
