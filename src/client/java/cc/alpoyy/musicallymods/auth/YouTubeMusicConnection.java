package cc.alpoyy.musicallymods.auth;

import cc.alpoyy.musicallymods.config.ModConfig;
import cc.alpoyy.musicallymods.music.impl.YtDlpManager;

import java.util.concurrent.CompletableFuture;

/**
 * YouTube Music has no official music API. This page reports the optional, unauthenticated yt-dlp
 * bridge honestly: there is no Google account, no cookie, no token and no "connected" claim.
 */
public final class YouTubeMusicConnection extends BaseConnection {
    private static final String EXPLANATION =
            "YouTube Music has no official music API. This mod never collects Google credentials, cookies "
                    + "or session tokens. The optional bridge uses a user-installed yt-dlp executable without "
                    + "login and may conflict with YouTube's Terms of Service.";

    YouTubeMusicConnection() {
        super("youtubemusic", PlatformConnection.State.NOT_CONNECTED,
                EXPLANATION, false);
        refreshView();
    }

    /** Enables or disables the opt-in bridge in the main config, then rechecks the executable. */
    public CompletableFuture<Void> setBridgeEnabled(boolean enabled) {
        ModConfig.get().integrations.youTubeMusicBridgeEnabled = enabled;
        ModConfig.markDirty();
        return refresh();
    }

    /** Re-runs filesystem detection and the bounded version check without opening a browser. */
    public CompletableFuture<Void> refresh() {
        return YtDlpManager.refreshAsync().thenAccept(diagnostic -> {
            boolean available = ModConfig.get().integrations.youTubeMusicBridgeEnabled
                    && diagnostic.usable();
            update(PlatformConnection.State.NOT_CONNECTED, "", "",
                    EXPLANATION + "  " + YtDlpManager.diagnostic().lastError(), false, available);
        });
    }

    /** No interactive login exists; the only user action is enabling the local bridge. */
    public CompletableFuture<Void> connect() {
        if (!ModConfig.get().integrations.youTubeMusicBridgeEnabled) {
            update(PlatformConnection.State.NOT_CONNECTED, "", "",
                    "Bridge is disabled. Enable it explicitly to check for a local yt-dlp executable. "
                            + EXPLANATION, false, false);
            return CompletableFuture.completedFuture(null);
        }
        return refresh();
    }

    @Override
    public CompletableFuture<Void> reconnect() {
        if (!ModConfig.get().integrations.youTubeMusicBridgeEnabled) {
            // Startup must never execute a third-party binary while the bridge is opt-in and disabled.
            YtDlpManager.clearCache();
            refreshView();
            return CompletableFuture.completedFuture(null);
        }
        return refresh();
    }

    /** Keeps the view truthful when detection changes from another screen. */
    public void refreshView() {
        YtDlpManager.Diagnostic diagnostic = YtDlpManager.diagnostic();
        boolean available = ModConfig.get().integrations.youTubeMusicBridgeEnabled && diagnostic.usable();
        String detail = EXPLANATION;
        if (!diagnostic.lastError().isBlank()) detail += "  " + diagnostic.lastError();
        update(PlatformConnection.State.NOT_CONNECTED, "", "", detail, false, available);
    }
}
