# Changelog

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
