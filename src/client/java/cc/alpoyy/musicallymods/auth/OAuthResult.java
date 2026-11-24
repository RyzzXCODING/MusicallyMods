package cc.alpoyy.musicallymods.auth;

import java.util.concurrent.CompletableFuture;

/** Immutable values shared by the PKCE helper and the provider connection managers. */
public final class OAuthResult {
    private OAuthResult() {
    }

    /** A prepared authorization request and its local loopback callback. */
    public record Pending(String authorizationUrl, String redirectUri, String codeVerifier,
                          String state, CompletableFuture<Callback> callback, Runnable cancel) {
    }

    /** The parameters returned to the loopback redirect URI; never trusted without a state check. */
    public record Callback(String code, String state, String error, String redirectUri) {
        public boolean valid() {
            return error == null && code != null && !code.isBlank() && state != null && !state.isBlank();
        }
    }

    /** A sanitized token response. Secrets are carried only inside the process, never logged. */
    public record Token(String accessToken, String refreshToken, long expiresInSeconds,
                        String scope, String error) {
        public boolean valid() {
            return error == null && accessToken != null && !accessToken.isBlank() && expiresInSeconds > 0;
        }
    }
}
