# MusicallyMods Implementation Plan

> **For agentic workers:** execute directly in this repository. No Gradle build/compile/run is permitted (explicit user policy); validation is static only.

**Goal:** Turn the existing 1.21.11 Fabric scaffold into a working, honest client-side music platform with real free-service integrations, playback, lyrics overlay, and persistent customization.

**Architecture:** Keep the existing split-source Fabric layout and Mojang official mappings. All gameplay/client code is client-only. Replace the fake provider stubs with capability-tagged services (Deezer 30s previews, Radio Browser stations, user-provided direct streams) plus explicit "not integrated" entries for Spotify/YouTube Music/SoundCloud. Use Java Sound + LGPL SPI decoders for MP3/OGG playback, Gson with field-by-field validation for config, immediate-mode Minecraft screen rendering for player/settings, LRCLIB for synced/plain lyrics, and Fabric's modern HUD element registry for the independent lyrics overlay.

**Tech Stack:** Java 21, Minecraft 1.21.11, Fabric Loader 0.20.1, Fabric API 0.141.6+1.21.11, Loom 1.19.4 (stable remap plugin), Gradle 9.2.1 wrapper, Gson (Minecraft runtime), java.net.http, Java Sound + dev.mccue mp3spi/vorbisspi 2024.04.19.

## Global Constraints

- Minecraft version exactly `1.21.11`; never change to another version.
- Java release 21; mappings `loom.officialMojangMappings()` (Mojang names, not Yarn).
- No build, compile, JAR generation, remapJar, or Minecraft launch.
- No credentials, tokens, cookies, or secrets in source or config defaults.
- No fake playback/search buttons; unsupported services show explicit reasons.
- Exact official icon already bundled: `assets/musicallymods/icon.png` (128x128, sha256 0ff25b5f...).

## Review Focus

1. Invalid/corrupt JSON must not crash the client; every field falls back independently.
2. Unsupported codecs/URLs must report a clear error instead of hanging or pretending to play.
3. Network calls must stay off the render/main thread and time out.
4. Lyrics must not claim synchronization when only plain text exists.
5. Config writes must not happen per frame or overwrite valid fields with invalid ones.

## Tasks

1. Build metadata + dependencies + icon verification: update `gradle.properties`, `build.gradle`, `fabric.mod.json`, docs; pin stable versions; add bundled audio decoder dependencies; confirm icon bytes.
2. Config foundation: `config/ModConfig.java` with nested colors/lyrics/keybinds, per-field validation, dirty-flag saves, atomic writes.
3. Music domain + services: `music/*` interfaces/models/manager, Deezer, Radio Browser, custom stream, unsupported service descriptions; `util/NetworkManager.java`.
4. Audio: `audio/AudioManager.java` with async open, streaming pump, pause/resume, volume, progress, cleanup, clear errors.
5. Lyrics: `lyrics/LyricsManager.java`, `lyrics/LrcLibService.java`, LRC parsing, cache, no fake sync.
6. UI support: `util/ArtworkCache.java`, `gui/Ui.java` immediate-mode helper.
7. Screens: `gui/MusicPlayerScreen.java`, `gui/MusicSettingsScreen.java`.
8. HUD + wiring: `hud/LyricsHud.java`, `client/KeybindManager.java`, rewrite `client/MusicallyModsClient.java`, translations, README/CHANGELOG/CREDITS.
9. Static audit: JSON validation, resource paths, API signature checks against resolved 1.21.11 jars, grep for secrets/stubs/TODOs, git diff review, no build artifacts.
10. Commit and push to existing `origin/master` on `RyzzXCODING/MusicallyMods` using authenticated `gh`/git; report honestly.
