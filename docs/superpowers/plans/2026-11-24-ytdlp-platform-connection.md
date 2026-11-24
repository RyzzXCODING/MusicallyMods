# yt-dlp Repair and Platform Connection Implementation Plan

> **For agentic workers:** execute directly in this repository. No Gradle build, compile, remap, JAR, launch or in-game test is permitted; validation is source/static review only.

**Goal:** Repair yt-dlp detection/execution and add independent, honest connection pages for YouTube Music, Spotify and SoundCloud to the existing mod.

**Architecture:** Replace the two-name PATH probe with a `YtDlpManager` that owns detection, health checks, diagnostics and optional verified installation; keep `YtDlpProcess` as the only structured process runner. Add a small auth layer (consent-gated restricted local session file, shared PKCE/loopback OAuth helper, one independent connection manager per provider) and per-provider `MusicService` implementations. Extend the existing immediate-mode GUI with a six-item navigation bar and a reusable platform page.

**Tech Stack:** Java 21, Minecraft 1.21.11 Mojang mappings, Fabric Loader 0.20.1, Gson, `java.net.http`/`HttpURLConnection`, `ServerSocket`, `MessageDigest`, Java Sound; no new dependencies.

**Spec:** `docs/superpowers/specs/2026-11-24-ytdlp-platform-connection-design.md`

## Global Constraints

- Minecraft `1.21.11`; Java `21`; mod id `musicallymods`; author Alpoyy; branding/icon unchanged.
- No build, compile, remap, JAR, Minecraft launch or automated in-game testing.
- No passwords, cookies, tokens, auth codes, client secrets or fake accounts in source, config defaults or logs.
- No MusicallyMods account system, backend, registration or cloud database.
- Never open an authorization browser window automatically; startup reconnect is silent.
- One provider's failure must not change another provider's state.
- Preserve existing player, lyrics HUD, keybinds, config fields and colors.

## Review Focus

1. A valid yt-dlp installed under an official platform-specific filename must be detected; pinned by a static review of the candidate-name table and detection order.
2. A file that exists but is not executable must be reported as a permission error, not "not found"; pinned by a review of the status mapping.
3. OAuth callback state, expiration and cancellation must never be trusted; pinned by a review of `OAuthClient` state checks and timeout handling.
4. Tokens must never enter the main config or logs; pinned by a diff grep for token field writes and log statements.
5. Scrollable GUI buttons must not be clickable outside the viewport; pinned by a review of rect intersection/scissor use.

## Tasks

### Task 1: YtDlpManager detection, health and diagnostics

**Files:** create `music/impl/YtDlpManager.java`; modify `music/impl/YtDlpProcess.java`, `config/ModConfig.java`.

- [x] Create `YtDlpManager` with `enum Status { NOT_INSTALLED, DETECTING, DETECTED, READY, INCOMPATIBLE, PERMISSION_ERROR, MISSING, FAILED }` and `record Diagnostic(Status status, String version, Path path, String source, String lastError, long checkedAt)`.
- [x] Implement ordered, OS/arch-aware candidate detection: configured path, mod-managed dependency directory, `PATH`, then known user install directories; include official standalone names for Windows/Linux/macOS and x86_64/x86/aarch64/armv7l.
- [x] Implement `refreshAsync()` that runs `<executable> --version` off-thread with a short timeout, parses a date-style version, maps `IOException` with permission hints to `PERMISSION_ERROR`, and caches an immutable `Diagnostic`.
- [x] Add `configurePath(String)`, `clearCache()`, `dependencyDirectory()`, `diagnostic()`, `executable()` and `shutdown()`.
- [x] Change `YtDlpProcess.findExecutable()` to delegate to `YtDlpManager.executable()`; keep the process API unchanged.
- [x] Verify statically that no path is quoted/split into a shell string and that no diagnostic contains a credential.
- [x] Commit: `fix: detect platform-specific yt-dlp binaries and report health`.

### Task 2: Safe process execution and optional verified install

**Files:** modify `music/impl/YtDlpProcess.java`, `music/impl/YtDlpService.java`; extend `util/NetworkManager.java`.

- [x] Set an explicit working directory and keep structured `ProcessBuilder` arguments; add a single-active-request guard keyed by resolver generation.
- [x] Gate compatibility-sensitive yt-dlp flags behind the detected version and keep only long-standing flags by default.
- [x] Add `NetworkManager.postForm(...)` and bounded `download(...)` with cancellation/progress callbacks; never use `BodyHandlers.limiting`.
- [x] Implement `YtDlpManager.installAsync(cancel, progress)`: official release asset plus official `SHA256SUMS`, verify digest before moving into the dependency directory, set POSIX executable bits where supported, and abort on any verification failure. GUI confirmation lives in Task 6.
- [x] Verify statically that output/exit codes are checked, descendants are destroyed, and no raw exception is shown.
- [x] Commit: `fix: harden yt-dlp process handling and optional install`.

### Task 3: Consent-gated local session storage

**Files:** create `auth/AuthStore.java`; extend `util/Errors.java`.

- [x] Store OAuth client configuration and per-provider sessions only in `config/musicallymods-auth.json` with owner-only permissions where the OS supports them; never in `musicallymods.json`.
- [x] Provide load/save/update/clear-session APIs with corruption isolation (a bad provider entry does not discard others).
- [x] Extend redaction to cover `code`, `state`, `code_verifier`, `refresh_token`, `access_token`, `id_token`, `Bearer` and cookie-shaped values; never log session values.
- [x] Verify statically that no token value reaches `MusicallyMods.LOGGER` or the main config writer.
- [x] Commit: `feat: add restricted local session storage with redaction`.

### Task 4: Shared PKCE OAuth helper

**Files:** create `auth/OAuthClient.java`, `auth/OAuthResult.java`.

- [x] Implement PKCE `S256` verifier/challenge generation, random state, loopback `ServerSocket` callback, exact state validation, error/`code` parsing, HTML response, 5-minute timeout and cancellation.
- [x] Implement token exchange/refresh POSTs with configurable public client ID and optional user-supplied secret; return sanitized errors only.
- [x] Open the official authorize URL through `Desktop`; on failure return the URL for the GUI to display/copy. Never open a browser during reconnect.
- [x] Verify statically that the redirect URI is loopback-only, no secret is hardcoded, and no code/token is logged.
- [x] Commit: `feat: add official PKCE authorization helper`.

### Task 5: Independent providers and auto-reconnect

**Files:** create `auth/PlatformConnection.java`, `auth/SpotifyConnection.java`, `auth/SoundCloudConnection.java`, `auth/YouTubeMusicConnection.java`, `music/impl/SpotifyService.java`; modify `music/ServiceManager.java`, `music/impl/YtDlpService.java`, `client/MusicallyModsClient.java`.

- [x] Define `ConnectionState` values: NOT_CONNECTED, CONNECTING, WAITING_FOR_AUTH, CONNECTED, RECONNECTING, TOKEN_EXPIRED, AUTHORIZATION_FAILED, DISCONNECTED, PLAYBACK_UNAVAILABLE, INTEGRATION_ERROR.
- [x] Implement one manager per provider with volatile `View` (state, account, error, detail, hasSession), independent connect/reconnect/disconnect and per-provider exception isolation.
- [x] Spotify: user-registered Client ID, minimal scopes, `/me` profile validation, token refresh, metadata search, device list and Connect-device play; raw in-game audio explicitly unsupported.
- [x] SoundCloud: follow the official documented flow; if credentials/approval are missing, report the exact requirement and keep search/connection unavailable instead of inventing endpoints.
- [x] YouTube Music: bridge readiness from `YtDlpManager`; no Google login and no cookies/tokens.
- [x] Wire silent `reconnectAll()` into client initialization after config load; shutdown cleanly on client stopping.
- [x] Replace the unavailable Spotify/SoundCloud entries with the new services and keep all existing services.
- [x] Verify statically that a provider failure cannot mutate another provider's view or delete saved settings.
- [x] Commit: `feat: add independent platform connections and silent reconnect`.

### Task 6: GUI navigation and platform pages

**Files:** create `gui/PlatformScreen.java`, `gui/GuiNav.java`; modify `gui/MusicPlayerScreen.java`, `gui/MusicSettingsScreen.java`.

- [x] Add a six-tab navigation bar: Home, YouTube Music, Spotify, SoundCloud, Lyrics, Settings.
- [x] Build one reusable, scrollable platform page with per-provider branding text, status, account info, connect/reconnect/disconnect, errors and a redacted Details section.
- [x] Add dependency diagnostics, retry, configure-path, install confirm/progress/cancel and consent text to the YouTube Music page; mirror a compact dependency block in Settings.
- [x] Add search/results/artwork and player controls to each page where the service supports them; unsupported actions stay disabled with an explanation.
- [x] Require a second click to confirm OAuth browser launch and session persistence; show the copied authorization URL fallback when the browser cannot be opened.
- [x] Verify statically that every button rect is clipped to its viewport, each page owns its own state, and no raw stack trace is rendered.
- [x] Commit: `feat: add platform connection GUI and dependency diagnostics`.

### Task 7: Documentation, source audit and release hygiene

**Files:** modify `README.md`, `CHANGELOG.md`.

- [x] Document the new detection order, optional install verification, OAuth requirements/limitations, storage limitation and navigation.
- [x] Audit Java, Fabric metadata, Gradle, keybinds, config migration, threading, cleanup, error handling and all new files for secrets, binaries, TODOs and fake states.
- [x] Run static checks only: `git diff --check`, secret/token grep, accidental binary grep, and a final review of changed files.
- [x] Commit and push to `origin/master` without force if `gh auth` and the remote remain valid.
- [x] Report the exact build-status line required by the user.
