# Changelog

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
