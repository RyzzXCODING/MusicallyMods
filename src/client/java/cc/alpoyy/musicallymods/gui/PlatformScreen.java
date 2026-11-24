package cc.alpoyy.musicallymods.gui;

import cc.alpoyy.musicallymods.audio.AudioManager;
import cc.alpoyy.musicallymods.auth.AuthStore;
import cc.alpoyy.musicallymods.auth.PlatformConnection;
import cc.alpoyy.musicallymods.auth.SoundCloudConnection;
import cc.alpoyy.musicallymods.auth.SpotifyConnection;
import cc.alpoyy.musicallymods.config.ModConfig;
import cc.alpoyy.musicallymods.lyrics.LyricsManager;
import cc.alpoyy.musicallymods.music.ServiceManager;
import cc.alpoyy.musicallymods.music.ServiceResult;
import cc.alpoyy.musicallymods.music.Track;
import cc.alpoyy.musicallymods.music.impl.YtDlpManager;
import cc.alpoyy.musicallymods.util.ArtworkCache;
import cc.alpoyy.musicallymods.util.Errors;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.awt.Desktop;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/** One reusable, clipped platform page with honest per-provider actions and diagnostics. */
public final class PlatformScreen extends Screen {
    public enum Provider {
        YOUTUBE_MUSIC("YouTube Music", "Optional unauthenticated yt-dlp bridge - no Google login"),
        SPOTIFY("Spotify", "Official Web API metadata and Spotify Connect device control"),
        SOUND_CLOUD("SoundCloud", "Official developer API metadata - no in-game HLS playback");

        private final String title;
        private final String subtitle;

        Provider(String title, String subtitle) {
            this.title = title;
            this.subtitle = subtitle;
        }
    }

    private record Action(Ui.Rect rect, Runnable onClick) {
    }

    private static final int MARGIN = 10;
    private static final int SCROLL_STEP = 44;
    private static final int ROW_HEIGHT = 32;

    private final Screen parent;
    private final Provider provider;
    private final GuiNav nav = new GuiNav();
    private final List<Action> actions = new ArrayList<>();
    private final AtomicBoolean installCancel = new AtomicBoolean(false);

    private Ui.Rect viewport;
    private Ui.Rect scrollbarRect;
    private int scroll;
    private int contentHeight;
    private String status = "Choose an action. Nothing is installed or opened automatically.";

    private String query = "";
    private boolean editingQuery;
    private boolean searching;
    private long searchGeneration;
    private List<Track> results = List.of();
    private int selectedResult = -1;

    private String pathText = "";
    private boolean editingPath;
    private boolean confirmAuth;
    private boolean confirmDisconnect;
    private boolean confirmInstall;
    private boolean confirmStorage;
    private boolean detailsOpen;
    private boolean installing;
    private String installStage = "";
    private long installDownloaded;
    private long installTotal;
    private String authUrlFallback = "";

    private List<SpotifyConnection.Device> devices = List.of();
    private SpotifyConnection.Device selectedDevice;
    private boolean loadingDevices;

    public PlatformScreen(Screen parent, Provider provider) {
        super(Component.literal("MusicallyMods - " + provider.title));
        this.parent = parent;
        this.provider = provider;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        ModConfig.Colors c = ModConfig.get().colors;
        actions.clear();
        graphics.fill(0, 0, width, height, c.mainBg);

        int navBottom = nav.render(graphics, font, MARGIN / 2, 2, Math.max(0, width - MARGIN), 18,
                width, height, navTab(), c, mouseX, mouseY);
        Ui.label(graphics, font, provider.title, MARGIN, navBottom + 4, c.textPrimary);
        Ui.label(graphics, font, Ui.fit(font, provider.subtitle, width - MARGIN * 2),
                MARGIN, navBottom + 18, c.textSecondary);

        int panelTop = Math.max(navBottom + 34, Math.min(60, height / 4));
        int footerY = height - 28;
        Ui.Rect panel = new Ui.Rect(MARGIN, panelTop, Math.max(0, width - MARGIN * 2),
                Math.max(0, footerY - panelTop - 6));
        Ui.panel(graphics, panel, c.secondaryBg, c.accent);
        int scrollWidth = ModConfig.get().ui.showScrollbars && panel.width() > 100 ? 6 : 0;
        viewport = new Ui.Rect(panel.x() + 4, panel.y() + 4,
                Math.max(0, panel.width() - 8 - scrollWidth), Math.max(0, panel.height() - 8));
        scrollbarRect = new Ui.Rect(panel.right() - scrollWidth - 2, viewport.y(),
                Math.max(0, scrollWidth - 2), viewport.height());
        scroll = Ui.clampScroll(scroll, Math.max(0, contentHeight - viewport.height()));

        try (Ui.Scissor ignored = Ui.scissor(graphics, viewport)) {
            renderConnection(graphics, c, mouseX, mouseY);
            if (provider == Provider.YOUTUBE_MUSIC) renderYouTube(graphics, c, mouseX, mouseY);
            else if (provider == Provider.SPOTIFY) renderSpotify(graphics, c, mouseX, mouseY);
            else renderSoundCloud(graphics, c, mouseX, mouseY);
        }
        scroll = Ui.clampScroll(scroll, Math.max(0, contentHeight - viewport.height()));
        if (ModConfig.get().ui.showScrollbars) {
            Ui.scrollbar(graphics, scrollbarRect, scroll,
                    Math.max(0, contentHeight - viewport.height()), viewport.height(),
                    Math.max(contentHeight, 1), dimColor(c.controls), c.accent);
        }

        Ui.label(graphics, font, Ui.fit(font, status, Math.max(0, width - MARGIN * 2)),
                MARGIN, footerY, c.textSecondary);
        Ui.Rect back = new Ui.Rect(width - MARGIN - 58, footerY - 4, 58, 18)
                .intersection(new Ui.Rect(0, 0, width, height));
        Ui.button(graphics, font, back, "Home", c.controls, c.accent, c.textPrimary, true,
                back.contains(mouseX, mouseY));
        actions.add(new Action(back, () -> GuiNav.open(minecraft, GuiNav.Tab.HOME, parent)));
    }

    private void renderConnection(GuiGraphics graphics, ModConfig.Colors c, int mouseX, int mouseY) {
        PlatformConnection.View view = view();
        int x = viewport.x() + 4;
        int w = Math.max(40, viewport.width() - 8);
        int y = 4;
        drawLabel(graphics, x, y, "Status: " + view.label(), c.textPrimary);
        y += 12;
        if (!view.account().isBlank()) {
            drawLabel(graphics, x, y, "Account: " + Ui.fit(font, view.account(), w - 12), c.textSecondary);
            y += 12;
        }
        for (String line : Ui.wrap(font, view.detail(), w, 5)) {
            drawLabel(graphics, x, y, line, c.textSecondary);
            y += 10;
        }
        if (!view.error().isBlank()) {
            drawLabel(graphics, x, y, "Error: " + Ui.fit(font, Errors.sanitize(view.error()), w - 12), c.accent);
            y += 12;
        }
        addButton(graphics, x, y, 66, 18, detailsOpen ? "Hide details" : "Details", true,
                () -> detailsOpen = !detailsOpen, c, mouseX, mouseY);
        y += 22;
        if (detailsOpen) {
            for (String line : Ui.wrap(font, redactedDetails(), w, 8)) {
                drawLabel(graphics, x, y, line, c.textSecondary);
                y += 10;
            }
            y += 6;
        }
        contentHeight = y + 4;
    }

    private void renderYouTube(GuiGraphics graphics, ModConfig.Colors c, int mouseX, int mouseY) {
        int x = viewport.x() + 4;
        int w = Math.max(40, viewport.width() - 8);
        int y = contentHeight;
        YtDlpManager.Diagnostic diagnostic = YtDlpManager.diagnostic();
        boolean enabled = ModConfig.get().integrations.youTubeMusicBridgeEnabled;

        drawLabel(graphics, x, y, "Dependency: yt-dlp", c.textPrimary);
        y += 13;
        drawLabel(graphics, x, y, "State: " + diagnostic.status() + (enabled ? " (bridge on)" : " (bridge off)"),
                c.textSecondary);
        y += 11;
        if (!diagnostic.version().isBlank()) {
            drawLabel(graphics, x, y, "Version: " + diagnostic.version(), c.textSecondary);
            y += 11;
        }
        if (diagnostic.path() != null) {
            drawLabel(graphics, x, y, "Path: " + Ui.fit(font, diagnostic.path().toString(), w), c.textSecondary);
            y += 11;
        }
        if (!diagnostic.source().isBlank()) {
            drawLabel(graphics, x, y, "Source: " + diagnostic.source(), c.textSecondary);
            y += 11;
        }
        if (!diagnostic.lastError().isBlank()) {
            for (String line : Ui.wrap(font, Errors.sanitize(diagnostic.lastError()), w, 4)) {
                drawLabel(graphics, x, y, line, c.accent);
                y += 10;
            }
        }
        addButton(graphics, x, y, 68, 18, "Refresh", true, this::refreshDependency, c, mouseX, mouseY);
        addButton(graphics, x + 72, y, 62, 18, "Configure", true, () -> {
            editingPath = true;
            pathText = ModConfig.get().integrations.youTubeMusicBridgeExecutable == null
                    ? "" : ModConfig.get().integrations.youTubeMusicBridgeExecutable;
            status = "Enter an absolute path or a path relative to the game directory.";
        }, c, mouseX, mouseY);
        addButton(graphics, x + 138, y, 54, 18, "Clear", true, () -> {
            pathText = "";
            ModConfig.get().integrations.youTubeMusicBridgeExecutable = "";
            ModConfig.markDirty();
            refreshDependency();
        }, c, mouseX, mouseY);
        addButton(graphics, x + 196, y, 76, 18, enabled ? "Disable bridge" : "Enable bridge", true,
                () -> setBridge(!enabled), c, mouseX, mouseY);
        y += 22;

        if (editingPath) {
            drawTextField(graphics, x, y, Math.min(w, 360), 20, pathText, true, c, mouseX, mouseY);
            addButton(graphics, x + Math.min(w, 360) + 4, y, 58, 20, "Apply", true,
                    this::applyPath, c, mouseX, mouseY);
            addButton(graphics, x + Math.min(w, 360) + 66, y, 58, 20, "Cancel", true,
                    () -> editingPath = false, c, mouseX, mouseY);
            y += 24;
        }

        drawLabel(graphics, x, y, "Optional install: official yt-dlp release + SHA2-256SUMS verification.",
                c.textSecondary);
        y += 12;
        drawLabel(graphics, x, y, "Managed directory: " + Ui.fit(font,
                YtDlpManager.dependencyDirectory().toString(), w), c.textSecondary);
        y += 12;
        if (installing) {
            drawLabel(graphics, x, y, installStage.isBlank() ? "Installing..." : installStage, c.textSecondary);
            y += 11;
            int barWidth = Math.max(20, w - 90);
            graphics.fill(x, y, x + barWidth, y + 8, c.controls);
            int filled = installTotal > 0 && barWidth > 0
                    ? (int) (barWidth * Math.min(1f, installDownloaded / (float) installTotal)) : 0;
            graphics.fill(x, y, x + filled, y + 8, c.progress);
            addButton(graphics, x + barWidth + 6, y - 6, 74, 18, "Cancel install", true,
                    this::cancelInstall, c, mouseX, mouseY);
            y += 14;
        } else {
            addButton(graphics, x, y, confirmInstall ? 142 : 112, 18,
                    confirmInstall ? "Confirm official install" : "Install official yt-dlp", true,
                    this::confirmOrInstall, c, mouseX, mouseY);
            if (confirmInstall) {
                addButton(graphics, x + 146, y, 58, 18, "Cancel", true,
                        () -> confirmInstall = false, c, mouseX, mouseY);
            }
            y += 22;
        }

        y += 6;
        drawLabel(graphics, x, y, "No Google account, cookie or token is used. Search is unauthenticated and may be restricted.",
                c.textSecondary);
        y += 14;
        addButton(graphics, x, y, 88, 18, "Play/Pause", true, AudioManager.get()::togglePlayPause,
                c, mouseX, mouseY);
        addButton(graphics, x + 92, y, 54, 18, "Stop", true, () -> {
            AudioManager.get().stop();
            LyricsManager.get().clear();
        }, c, mouseX, mouseY);
        y += 24;

        y = renderSearch(graphics, c, mouseX, mouseY, x, w, y, "youtubemusic",
                "Search the unauthenticated bridge");
        addButton(graphics, x, y, 96, 18, "Play in player", selectedResult >= 0 && selectedResult < results.size(),
                () -> {
                    Track track = selectedTrack();
                    if (track == null) return;
                    AudioManager.get().setQueue(results);
                    AudioManager.get().playIndex(selectedResult);
                    LyricsManager.get().setTrack(track);
                    ArtworkCache.request(track);
                    status = "Selected " + track.displayTitle() + ". Player controls are above.";
                }, c, mouseX, mouseY);
        y += 22;
        contentHeight = y + 4;
    }

    private void renderSpotify(GuiGraphics graphics, ModConfig.Colors c, int mouseX, int mouseY) {
        int x = viewport.x() + 4;
        int w = Math.max(40, viewport.width() - 8);
        int y = contentHeight;
        SpotifyConnection connection = PlatformConnection.spotify();
        y = renderConsent(graphics, c, mouseX, mouseY, x, w, y);
        if (!connection.configured()) {
            y = renderWrapped(graphics, x, y, w,
                    "A public Client ID is required. Register an app in the Spotify Developer Dashboard with "
                            + "redirect URI http://127.0.0.1 (no port), then set spotifyClientId in "
                            + "config/musicallymods-auth.json. No client secret is embedded.", c.textSecondary);
        }
        y = renderAuthButtons(graphics, c, mouseX, mouseY, x, y);
        if (!authUrlFallback.isBlank()) {
            y = renderWrapped(graphics, x, y, w,
                    "If the browser did not open, copy this official authorization URL: " + authUrlFallback,
                    c.textSecondary);
        }
        y += 4;

        if (view().state() == PlatformConnection.State.CONNECTED) {
            drawLabel(graphics, x, y, "Spotify Connect devices", c.textPrimary);
            y += 13;
            addButton(graphics, x, y, 92, 18, "Refresh devices", true, this::refreshDevices, c, mouseX, mouseY);
            y += 22;
            if (loadingDevices) {
                drawLabel(graphics, x, y, "Loading devices...", c.textSecondary);
                y += 12;
            } else if (devices.isEmpty()) {
                drawLabel(graphics, x, y, "No devices returned. Start Spotify on a Premium, unrestricted device.",
                        c.textSecondary);
                y += 12;
            } else {
                for (int i = 0; i < devices.size(); i++) {
                    SpotifyConnection.Device device = devices.get(i);
                    Ui.Rect row = rect(x, y, w, 26);
                    Ui.panel(graphics, row, device == selectedDevice ? c.controls : c.mainBg,
                            device == selectedDevice ? c.accent : 0);
                    Ui.label(graphics, font, Ui.fit(font, (i + 1) + ". " + device.label(), w - 8),
                            row.x() + 4, row.y() + 7, c.textPrimary);
                    addClick(row, () -> selectedDevice = device);
                    y += 28;
                }
            }
            y += 4;
        }

        y = renderSearch(graphics, c, mouseX, mouseY, x, w, y, "spotify", "Search Spotify metadata");
        addButton(graphics, x, y, 132, 18, "Send to Connect device",
                selectedResult >= 0 && selectedResult < results.size() && selectedDevice != null,
                this::playOnSpotify, c, mouseX, mouseY);
        y += 22;
        drawLabel(graphics, x, y, "Raw Spotify audio is not exposed to Minecraft; use an active Connect device.",
                c.textSecondary);
        y += 12;
        contentHeight = y + 4;
    }

    private void renderSoundCloud(GuiGraphics graphics, ModConfig.Colors c, int mouseX, int mouseY) {
        int x = viewport.x() + 4;
        int w = Math.max(40, viewport.width() - 8);
        int y = contentHeight;
        SoundCloudConnection connection = PlatformConnection.soundCloud();
        y = renderConsent(graphics, c, mouseX, mouseY, x, w, y);
        y = renderWrapped(graphics, x, y, w, SoundCloudConnectionRequirement.text(), c.textSecondary);
        y = renderAuthButtons(graphics, c, mouseX, mouseY, x, y);
        if (!authUrlFallback.isBlank()) {
            y = renderWrapped(graphics, x, y, w,
                    "If the browser did not open, copy this official authorization URL: " + authUrlFallback,
                    c.textSecondary);
        }
        y = renderSearch(graphics, c, mouseX, mouseY, x, w, y, "soundcloud",
                "Search playable SoundCloud metadata");
        addButton(graphics, x, y, 116, 18, "Open on SoundCloud",
                selectedResult >= 0 && selectedResult < results.size(), this::openSelectedExternal,
                c, mouseX, mouseY);
        y += 22;
        drawLabel(graphics, x, y, "Official HLS streams cannot be decoded by this mod's Java audio player.",
                c.textSecondary);
        y += 12;
        contentHeight = y + 4;
    }

    private int renderConsent(GuiGraphics graphics, ModConfig.Colors c, int mouseX, int mouseY,
                              int x, int w, int y) {
        if (AuthStore.get().storageConsent) return y;
        y = renderWrapped(graphics, x, y, w, AuthStore.storageNote(), c.textSecondary);
        addButton(graphics, x, y, confirmStorage ? 158 : 118, 18,
                confirmStorage ? "Confirm: enable storage" : "Enable local storage", true,
                () -> {
                    if (!confirmStorage) {
                        confirmStorage = true;
                        status = "Confirm once more to save sessions locally on this device.";
                        return;
                    }
                    AuthStore.get().storageConsent = true;
                    AuthStore.save(AuthStore.get());
                    confirmStorage = false;
                    status = "Local session storage enabled. Client credentials stay in the auth file only.";
                }, c, mouseX, mouseY);
        return y + 22;
    }

    private int renderAuthButtons(GuiGraphics graphics, ModConfig.Colors c, int mouseX, int mouseY,
                                  int x, int y) {
        boolean connected = view().hasSession();
        addButton(graphics, x, y, confirmAuth ? 138 : 92, 18,
                confirmAuth ? "Confirm official login" : "Connect", true, this::confirmOrConnect,
                c, mouseX, mouseY);
        addButton(graphics, x + 142, y, 78, 18, "Reconnect", true, this::reconnect, c, mouseX, mouseY);
        addButton(graphics, x + 224, y, confirmDisconnect ? 144 : 94, 18,
                confirmDisconnect ? "Confirm disconnect" : "Disconnect", connected,
                this::confirmOrDisconnect, c, mouseX, mouseY);
        if (confirmAuth) {
            addButton(graphics, x + (confirmDisconnect ? 372 : 322), y, 58, 18, "Cancel", true,
                    () -> {
                        confirmAuth = false;
                        authUrlFallback = "";
                    }, c, mouseX, mouseY);
        }
        return y + 22;
    }

    private int renderSearch(GuiGraphics graphics, ModConfig.Colors c, int mouseX, int mouseY,
                             int x, int w, int y, String serviceId, String label) {
        drawLabel(graphics, x, y, label, c.textPrimary);
        y += 13;
        int fieldWidth = Math.max(40, Math.min(w - 76, 320));
        drawTextField(graphics, x, y, fieldWidth, 20, query, editingQuery, c, mouseX, mouseY);
        addButton(graphics, x + fieldWidth + 5, y, 64, 20, searching ? "..." : "Search", !searching,
                () -> doSearch(serviceId), c, mouseX, mouseY);
        y += 24;

        if (results.isEmpty()) {
            drawLabel(graphics, x, y, searching ? "Searching..." : "No results loaded.", c.textSecondary);
            return y + 13;
        }
        for (int i = 0; i < results.size(); i++) {
            Track track = results.get(i);
            Ui.Rect row = rect(x, y, w, ROW_HEIGHT);
            if (!row.intersects(viewport)) {
                y += ROW_HEIGHT + 2;
                continue;
            }
            boolean selected = i == selectedResult;
            Ui.panel(graphics, row, selected ? c.controls : c.mainBg, selected ? c.accent : 0);
            int artSize = Math.min(28, ROW_HEIGHT - 4);
            if (!track.artworkUrl().isBlank()) {
                ArtworkCache.request(track);
                Ui.panel(graphics, rect(row.x() + 2, row.y() + 2, artSize, artSize), c.secondaryBg, c.accent);
                if (ArtworkCache.has(track)) {
                    ArtworkCache.render(graphics, track, row.x() + 2, row.y() + 2, artSize, artSize);
                }
            }
            int textX = row.x() + artSize + 8;
            int textW = Math.max(0, row.right() - textX - 4);
            Ui.label(graphics, font, Ui.fit(font, (i + 1) + ". " + track.displayTitle(), textW),
                    textX, row.y() + 3, c.textPrimary);
            Ui.label(graphics, font, Ui.fit(font, track.displayArtist() + " - " + track.note(), textW),
                    textX, row.y() + 16, c.textSecondary);
            final int index = i;
            addClick(row, () -> selectedResult = index);
            y += ROW_HEIGHT + 2;
        }
        return y + 4;
    }

    private int renderWrapped(GuiGraphics graphics, int x, int y, int w, String text, int color) {
        for (String line : Ui.wrap(font, text, w, 8)) {
            drawLabel(graphics, x, y, line, color);
            y += 10;
        }
        return y + 2;
    }

    private String redactedDetails() {
        StringBuilder b = new StringBuilder();
        PlatformConnection.View view = view();
        if (!view.error().isBlank()) b.append("Redacted error: ").append(Errors.sanitize(view.error())).append(". ");
        if (provider == Provider.YOUTUBE_MUSIC) {
            YtDlpManager.Diagnostic d = YtDlpManager.diagnostic();
            b.append("Dependency source: ").append(d.source()).append(". ");
            b.append("Path: ").append(d.path() == null ? "none" : d.path()).append(". ");
            b.append("Managed-install checksum: ")
                    .append(YtDlpManager.lastInstallVerified() ? "verified" : "not verified").append(". ");
        } else {
            b.append("Storage: ").append(AuthStore.storageNote());
        }
        b.append("No tokens, authorization codes, cookies or stack traces are displayed.");
        return b.toString();
    }

    private void setBridge(boolean enabled) {
        PlatformConnection.youTubeMusic().setBridgeEnabled(enabled)
                .whenComplete((ignored, throwable) -> minecraft.execute(() -> {
                    status = throwable == null
                            ? "Bridge " + (enabled ? "enabled" : "disabled") + "."
                            : "Bridge update failed: " + Errors.describe(throwable);
                    PlatformConnection.youTubeMusic().refreshView();
                }));
    }

    private void refreshDependency() {
        status = "Checking the local executable...";
        PlatformConnection.youTubeMusic().refresh().whenComplete((ignored, throwable) -> minecraft.execute(() -> {
            status = throwable == null
                    ? YtDlpManager.diagnostic().lastError()
                    : "Detection failed: " + Errors.describe(throwable);
            if (status.isBlank()) status = "yt-dlp is ready.";
        }));
    }

    private void applyPath() {
        editingPath = false;
        status = "Checking configured path...";
        YtDlpManager.configurePath(pathText).whenComplete((diagnostic, throwable) -> minecraft.execute(() -> {
            if (throwable != null) status = "Path check failed: " + Errors.describe(throwable);
            else status = diagnostic.status() + (diagnostic.lastError().isBlank() ? "" : ": " + diagnostic.lastError());
            PlatformConnection.youTubeMusic().refreshView();
        }));
    }

    private void confirmOrInstall() {
        if (!confirmInstall) {
            confirmInstall = true;
            status = "Confirm once more to download and verify the official yt-dlp release.";
            return;
        }
        confirmInstall = false;
        installing = true;
        installCancel.set(false);
        installStage = "Preparing official download";
        status = "Installing from the official release. Checksums are verified before use.";
        YtDlpManager.installAsync(installCancel, progress -> minecraft.execute(() -> {
            installStage = progress.stage();
            installDownloaded = progress.downloaded();
            installTotal = progress.total();
        })).whenComplete((result, throwable) -> minecraft.execute(() -> {
            installing = false;
            status = throwable != null ? "Install failed: " + Errors.describe(throwable)
                    : result.message();
            refreshDependency();
        }));
    }

    private void cancelInstall() {
        installCancel.set(true);
        status = "Cancellation requested. No unverified executable will be installed.";
    }

    private void confirmOrConnect() {
        if (!confirmAuth) {
            confirmAuth = true;
            authUrlFallback = "";
            status = "Confirm once more to open the official " + provider.title + " authorization page.";
            return;
        }
        confirmAuth = false;
        status = "Starting official authorization. Approve it in your browser, then return here.";
        if (provider == Provider.SPOTIFY) {
            PlatformConnection.spotify().connect()
                    .whenComplete((ignored, throwable) -> minecraft.execute(() -> afterConnect(throwable,
                            PlatformConnection.spotify().lastAuthorizationUrl())));
        } else if (provider == Provider.SOUND_CLOUD) {
            PlatformConnection.soundCloud().connect()
                    .whenComplete((ignored, throwable) -> minecraft.execute(() -> afterConnect(throwable,
                            PlatformConnection.soundCloud().lastAuthorizationUrl())));
        }
    }

    private void afterConnect(Throwable throwable, String url) {
        authUrlFallback = url == null ? "" : url;
        if (throwable != null) status = "Authorization failed: " + Errors.describe(throwable);
        else if (!view().error().isBlank()) status = "Authorization failed: " + Errors.sanitize(view().error());
    }

    private void reconnect() {
        status = "Silently checking the saved session...";
        if (provider == Provider.YOUTUBE_MUSIC) refreshDependency();
        else if (provider == Provider.SPOTIFY) {
            PlatformConnection.spotify().reconnect()
                    .whenComplete((ignored, throwable) -> minecraft.execute(() -> afterReconnect(throwable)));
        } else {
            PlatformConnection.soundCloud().reconnect()
                    .whenComplete((ignored, throwable) -> minecraft.execute(() -> afterReconnect(throwable)));
        }
    }

    private void afterReconnect(Throwable throwable) {
        status = throwable == null ? "Reconnect check finished: " + view().label()
                : "Reconnect failed: " + Errors.describe(throwable);
    }

    private void confirmOrDisconnect() {
        if (!confirmDisconnect) {
            confirmDisconnect = true;
            status = "Confirm once more to remove this provider's saved session.";
            return;
        }
        confirmDisconnect = false;
        clearTransient();
        if (provider == Provider.YOUTUBE_MUSIC) {
            setBridge(false);
            status = "Bridge disabled. No Google credentials exist to clear.";
        } else if (provider == Provider.SPOTIFY) {
            PlatformConnection.spotify().disconnect()
                    .whenComplete((ignored, throwable) -> minecraft.execute(() -> afterDisconnect(throwable)));
        } else {
            PlatformConnection.soundCloud().disconnect()
                    .whenComplete((ignored, throwable) -> minecraft.execute(() -> afterDisconnect(throwable)));
        }
    }

    private void afterDisconnect(Throwable throwable) {
        status = throwable == null ? "Saved session removed from this device."
                : "Local session removed; remote revocation failed: " + Errors.describe(throwable);
    }

    private void clearTransient() {
        confirmAuth = false;
        confirmInstall = false;
        authUrlFallback = "";
        results = List.of();
        selectedResult = -1;
        devices = List.of();
        selectedDevice = null;
    }

    private void doSearch(String serviceId) {
        if (query.isBlank()) {
            status = "Enter a search query first.";
            return;
        }
        searching = true;
        results = List.of();
        selectedResult = -1;
        status = "Searching " + provider.title + "...";
        long generation = ++searchGeneration;
        CompletableFuture<ServiceResult<List<Track>>> future = ServiceManager.get(serviceId)
                .map(service -> service.search(query))
                .orElseGet(() -> CompletableFuture.completedFuture(
                        ServiceResult.failure("That service is not available.")));
        future.whenComplete((result, throwable) -> minecraft.execute(() -> {
            if (generation != searchGeneration) return;
            searching = false;
            if (throwable != null) {
                status = "Search failed: " + Errors.describe(throwable);
            } else if (result == null || !result.successful()) {
                status = result == null ? "Search failed." : result.error();
            } else {
                results = result.value() == null ? List.of() : result.value();
                status = results.isEmpty() ? "No results." : results.size() + " results. Select one below.";
            }
        }));
    }

    private void refreshDevices() {
        loadingDevices = true;
        status = "Loading Spotify Connect devices...";
        PlatformConnection.spotify().devices().whenComplete((list, throwable) -> minecraft.execute(() -> {
            loadingDevices = false;
            if (throwable != null) {
                status = "Device list failed: " + Errors.describe(throwable);
                return;
            }
            devices = list == null ? List.of() : list;
            selectedDevice = devices.isEmpty() ? null : devices.get(0);
            status = devices.isEmpty() ? "No Spotify Connect devices were returned."
                    : devices.size() + " devices found.";
        }));
    }

    private void playOnSpotify() {
        Track track = selectedTrack();
        if (track == null || selectedDevice == null) return;
        status = "Sending track to Spotify Connect...";
        PlatformConnection.spotify().play(selectedDevice, "spotify:track:" + track.id())
                .whenComplete((message, throwable) -> minecraft.execute(() -> status =
                        throwable == null ? message : "Connect control failed: " + Errors.describe(throwable)));
    }

    private void openSelectedExternal() {
        Track track = selectedTrack();
        if (track == null || track.streamUrl().isBlank()) {
            status = "This result has no official SoundCloud page URL.";
            return;
        }
        try {
            URI uri = URI.create(track.streamUrl());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                status = "Only https SoundCloud URLs can be opened.";
                return;
            }
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                status = "Open this URL manually: " + track.streamUrl();
                return;
            }
            Desktop.getDesktop().browse(uri);
            status = "Opened the track on SoundCloud. Attribution is shown in the result note.";
        } catch (Exception e) {
            status = "Open failed: " + Errors.describe(e);
        }
    }

    private Track selectedTrack() {
        return selectedResult >= 0 && selectedResult < results.size() ? results.get(selectedResult) : null;
    }

    private PlatformConnection.View view() {
        return switch (provider) {
            case YOUTUBE_MUSIC -> PlatformConnection.youTubeMusic().view();
            case SPOTIFY -> PlatformConnection.spotify().view();
            case SOUND_CLOUD -> PlatformConnection.soundCloud().view();
        };
    }

    private GuiNav.Tab navTab() {
        return switch (provider) {
            case YOUTUBE_MUSIC -> GuiNav.Tab.YOUTUBE_MUSIC;
            case SPOTIFY -> GuiNav.Tab.SPOTIFY;
            case SOUND_CLOUD -> GuiNav.Tab.SOUND_CLOUD;
        };
    }

    private void drawTextField(GuiGraphics graphics, int x, int y, int w, int h, String value,
                               boolean editing, ModConfig.Colors c, int mouseX, int mouseY) {
        Ui.Rect rect = rect(x, screenY(y), w, h);
        Ui.Rect visible = rect.intersection(viewport);
        if (visible.width() <= 0 || visible.height() <= 0) return;
        Ui.panel(graphics, visible, c.mainBg, editing ? c.textPrimary : c.accent);
        String shown = editing ? value + "_" : (value.isBlank() ? "..." : value);
        Ui.label(graphics, font, Ui.fit(font, shown, visible.width() - 8),
                visible.x() + 4, visible.y() + (h - font.lineHeight) / 2, c.textPrimary);
        if (!editing) {
            addClick(rect, () -> {
                editingQuery = provider != Provider.YOUTUBE_MUSIC;
                editingPath = provider == Provider.YOUTUBE_MUSIC;
            });
        }
    }

    private void addButton(GuiGraphics graphics, int x, int yContent, int w, int h, String label,
                           boolean enabled, Runnable onClick, ModConfig.Colors c, int mouseX, int mouseY) {
        Ui.Rect visible = rect(x, screenY(yContent), w, h).intersection(viewport);
        if (visible.width() <= 0 || visible.height() <= 0) return;
        Ui.button(graphics, font, visible, label, c.controls, c.accent, c.textPrimary,
                enabled, visible.contains(mouseX, mouseY));
        if (enabled) addClick(rect(x, screenY(yContent), w, h), onClick);
    }

    private void drawLabel(GuiGraphics graphics, int x, int yContent, String text, int color) {
        Ui.label(graphics, font, Ui.fit(font, text, Math.max(0, viewport.width() - 8)),
                x, screenY(yContent), color);
    }

    private void addClick(Ui.Rect contentRect, Runnable onClick) {
        Ui.Rect visible = contentRect.intersection(viewport);
        if (onClick != null && visible.width() > 0 && visible.height() > 0) {
            actions.add(new Action(visible, onClick));
        }
    }

    private Ui.Rect rect(int x, int y, int w, int h) {
        return new Ui.Rect(x, y, Math.max(0, w), Math.max(0, h));
    }

    private int screenY(int contentY) {
        return viewport.y() + contentY - scroll;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (event.button() != 0) return false;
        GuiNav.Tab tab = nav.clicked(event.x(), event.y());
        if (tab != null) {
            GuiNav.open(minecraft, tab, parent);
            return true;
        }
        for (Action action : actions) {
            if (action.rect().contains(event.x(), event.y())) {
                action.onClick().run();
                return true;
            }
        }
        if (viewport != null && scrollbarRect != null && viewport.contains(event.x(), event.y())
                && scrollbarRect.width() > 0 && scrollbarRect.contains(event.x(), event.y())) {
            double fraction = (event.y() - scrollbarRect.y()) / (double) Math.max(1, scrollbarRect.height());
            scroll = Ui.clampScroll((int) (fraction * Math.max(0, contentHeight - viewport.height())),
                    Math.max(0, contentHeight - viewport.height()));
            return true;
        }
        editingPath = false;
        editingQuery = false;
        return viewport != null && viewport.contains(event.x(), event.y());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amountX, double amountY) {
        if (viewport != null && viewport.contains(mouseX, mouseY)) {
            scroll = Ui.clampScroll(scroll - (int) Math.signum(amountY) * SCROLL_STEP,
                    Math.max(0, contentHeight - viewport.height()));
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (editingPath || editingQuery) {
            if (event.key() == GLFW.GLFW_KEY_BACKSPACE) {
                if (editingPath && !pathText.isEmpty()) pathText = pathText.substring(0, pathText.length() - 1);
                if (editingQuery && !query.isEmpty()) query = query.substring(0, query.length() - 1);
                return true;
            }
            if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
                if (editingPath) applyPath();
                else doSearch(provider == Provider.YOUTUBE_MUSIC ? "youtubemusic"
                        : provider == Provider.SPOTIFY ? "spotify" : "soundcloud");
                return true;
            }
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                editingPath = false;
                editingQuery = false;
                return true;
            }
            return false;
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            ModConfig.saveIfDirty();
            minecraft.setScreen(parent);
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_PAGE_UP) {
            scroll = Ui.clampScroll(scroll - SCROLL_STEP * 2, Math.max(0, contentHeight - viewport.height()));
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_PAGE_DOWN) {
            scroll = Ui.clampScroll(scroll + SCROLL_STEP * 2, Math.max(0, contentHeight - viewport.height()));
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        int codepoint = event.codepoint();
        if (Character.isISOControl(codepoint)) return false;
        String typed = new String(Character.toChars(codepoint));
        if (editingPath && pathText.length() < 512) {
            pathText += typed;
            return true;
        }
        if (editingQuery && query.length() < 160) {
            query += typed;
            return true;
        }
        return false;
    }

    @Override
    public void onClose() {
        ModConfig.saveIfDirty();
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static int dimColor(int color) {
        int r = ((color >> 16) & 0xFF) * 70 / 100;
        int g = ((color >> 8) & 0xFF) * 70 / 100;
        int b = (color & 0xFF) * 70 / 100;
        return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    /** Keeps provider prerequisite wording in one place without embedding credentials. */
    private static final class SoundCloudConnectionRequirement {
        private SoundCloudConnectionRequirement() {
        }

        static String text() {
            return "SoundCloud's current API requires a registered app, an Artist Pro subscription, and your own "
                    + "Client ID and Client Secret. Register the exact redirect URI http://127.0.0.1:"
                    + SoundCloudConnection.REDIRECT_PORT + " and set both credentials in "
                    + "config/musicallymods-auth.json. Nothing is bundled or guessed.";
        }
    }
}
