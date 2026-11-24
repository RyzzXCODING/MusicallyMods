# Changelog

## 0.3.0

- Replaced one-shot PATH/executable detection with OS/arch-aware, ordered detection across configured path,
  mod-managed dependency directory, `PATH` and common user install locations. Timestamped `--version`
  health checks now report version, source, path, last error, permission problems and incompatible builds.
- Hardened yt-dlp process handling: structured argv only, no shell, bounded output/timeouts, asynchronous
  checks, cancellation, descendant cleanup, explicit exit-code handling and truthful diagnostics.
- Added an explicit two-click opt-in installer using only official yt-dlp release assets and
  `SHA2-256SUMS`, with SHA-256 verification before the executable is moved, POSIX permission setting,
  progress, cancellation and failure recovery.
- Added consent-gated local session storage in `config/musicallymods-auth.json` with owner-only POSIX
  permissions, per-provider corruption isolation, redaction and no tokens in main config or logs.
- Added a shared official OAuth 2.0/2.1 PKCE helper with IPv4 loopback callback, exact state validation,
  cancellation and 5-minute timeout. No browser opens during silent startup reconnect. Restored sessions
  are shown as Reconnecting until the official profile endpoint validates them.
- Startup never executes a detected yt-dlp binary while the opt-in bridge is disabled; `--version` health
  checks run only after explicit enablement or a manual Retry. Checksum status stays unverified until an
  official asset has actually been verified.
- Added independent platform managers for Spotify, SoundCloud and YouTube Music. Spotify uses a public
  Client ID with Web API metadata/search, profile validation, refresh and Spotify Connect device control;
  raw audio remains unsupported. SoundCloud uses the current official developer API with the user's own
  confidential credentials, exact registered loopback redirect, playable-only metadata search,
  attribution and official disconnect; HLS playback remains unsupported. YouTube Music remains an
  unauthenticated, opt-in bridge with no Google credentials.
- Added six-tab navigation (Home, YouTube Music, Spotify, SoundCloud, Lyrics, Settings) and one reusable,
  responsive platform page with status/account info, redacted details, connectivity actions, dependency
  diagnostics, artwork/results where supported and clipped scroll regions.
- Preserved existing player, lyrics HUD, keybinds, colors, config schema `2` fields and migration behavior.

`0.3.0` was built with `./gradlew clean build`. The remapped client JAR reports version `0.3.0` with the
expected client entrypoint and dependency ranges, contains all six bundled decoder JARs, keeps the official
icon (SHA-256 `0ff25b5f…fc11dd`) and Java 21 bytecode (class major 65). The release JAR SHA-256 is
`3090bcdc…10768`. Minecraft itself was not launched.

## 0.2.1

- Replaced ambiguous playback booleans with a single authoritative state machine (`IDLE`, `LOADING`,
  `BUFFERING`, `PLAYING`, `PAUSED`, `STOPPED`, `FAILED`), generation/cancel guards, bounded retry,
  safe cleanup and honest failure messages. No simulated playback remains.
- Bounded audio stream opening with connect/read timeouts and guaranteed connection/stream teardown;
  network and external-process work is asynchronous and capped.
- Added an optional, disabled-by-default YouTube Music bridge for a user-installed `yt-dlp` executable.
  Structured argv only; no shell, credentials, bundled/downloaded binary, impersonation, DRM/premium bypass,
  auto-update, plugins or cookies.
- Replaced generic "Not Integrated" labels with explicit integration level, playback support and auth
  requirement. Spotify remains unintegrated: official Web API only, user OAuth/Premium/Connect-device limits
  are documented. SoundCloud requires developer authorization.
- Reworked the player and settings GUIs with fixed header/player areas, clipped scrolling result and settings
  pages, clamped scrollbars, visible-region-only actions, and half-open hit regions.
- Added per-field config validation and schema `2` (`playback`, `integrations`, `ui` sections), preserving
  backward compatibility with older files and safe defaults.
- Clamped and scissored the lyrics HUD, surfaced lookup errors honestly, and kept unsynced lyrics unhighlighted.
- Built with `./gradlew build` (Gradle 9.9.2, Loom 1.19.4, Java 25 build JVM). Verified Java 21 bytecode
  (class major 65), exact official icon, nested audio decoder JARs and correct metadata.

## 0.2.0

- Replaced fake provider stubs with explicit capability declarations and three real integrations:
  Deezer 30-second previews, Radio Browser stations, and user-provided direct audio URLs.
- Added honest, non-interactive entries for Spotify, YouTube Music and SoundCloud that explain
  why they are not integrated; no credentials, tokens, cookies or DRM bypasses are included.
- Added asynchronous search with timeouts, error reporting and artwork loading off the render thread.
- Added Java Sound streaming playback with pause/resume, stop, previous/next, volume and progress.
  MP3 and OGG Vorbis decoders are bundled; unsupported codecs are reported.
- Added LRCLIB lyrics with real synced highlighting only when timestamps exist; plain lyrics are
  clearly labelled as unsynced.
- Replaced the scaffold screens with a functional player GUI and settings GUI.
- Added a customizable independent lyrics overlay using Fabric's modern HUD element registry.
- Reworked configuration into validated, per-field-safe JSON with atomic writes and theme/color editing.
- Updated stable version pins: Loader 0.20.1, Fabric API 0.141.6+1.21.11, Loom 1.19.4, Java 21.
- Bundled the exact official 128×128 icon unchanged.

## 0.1.0

- Initial project scaffold for MusicallyMods (Fabric 1.21.11, Java 21, client-side).
