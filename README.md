# MusicallyMods

MusicallyMods is a **client-only** Fabric mod for Minecraft **1.21.11** (Java **21**) that provides a lightweight in-game music player, asynchronous search, real playback where the source legally permits it, artwork, and a customizable independent lyrics HUD.

> **Honest scope:** this mod does not include accounts, passwords, cookies, tokens or DRM/access-control circumvention. Spotify uses the official Web API only with your own registered public Client ID and Authorization Code + PKCE; raw Spotify audio is never streamed into Minecraft. SoundCloud uses the current official developer API only with your own registered confidential app credentials; its HLS streams are not decoded by this mod's Java audio player. YouTube Music has no official API here: it remains an opt-in, disabled-by-default, unauthenticated `yt-dlp` bridge. No Google login data is collected. Opt-in installation of official `yt-dlp` release assets is possible only after explicit confirmation and checksum verification; nothing is bundled or silently downloaded.

## Version contract

| Component | Version |
| --- | --- |
| Minecraft | `1.21.11` (exact; never retargeted) |
| Java | `21` |
| Fabric Loader | `0.20.1` |
| Fabric API | `0.141.6+1.21.11` |
| Loom | `1.19.4` (stable `fabric-loom-remap`) |
| Mappings | Official Mojang mappings (`loom.officialMojangMappings()`) |
| Gradle wrapper | `9.9.2` (Loom 1.19.4 requires ≥9.8.0 and a Java 25 build JVM; mod bytecode still targets Java 21) |

## Working integrations

### Deezer previews

- Uses Deezer's public, key-less search API.
- Shows track title, artist, album, duration and cover artwork.
- Plays **official 30-second previews only**. It does not stream full songs, bypass premium restrictions, or ship credentials.
- Search, metadata, artwork, pause/resume and skip are real controls.

### Internet Radio (Radio Browser)

- Uses the free, key-less Radio Browser station directory.
- Searches **stations**, not individual songs.
- Plays live streams whose codec is supported by the bundled decoders (`MP3`, `OGG`/`Vorbis`, `WAV`). Unsupported codecs are reported clearly instead of pretending to play.
- No song-level lyrics or playlists are claimed.

### Direct stream URL

- Paste a legal direct `http(s)` audio URL into the search field.
- No metadata service, account, or playlist is invented for these URLs.
- Format support is limited to the bundled Java Sound decoders plus JDK-supported formats.

### YouTube Music (optional external bridge)

- Disabled by default. Enable `integrations.youTubeMusicBridgeEnabled` only if you accept the risks.
- Detection order: configured path, mod-managed `musicallymods/dependencies/yt-dlp`, `PATH`, then common
  user install locations. Official standalone names are checked for Windows x86/x64/ARM64,
  Linux glibc/musl x86_64/aarch64, and macOS. Timestamped `--version` health checks verify usability;
  non-executable files are reported as permission errors rather than "not found".
- The YouTube Music tab shows state, version, path, source, last error, Retry, Configure path, Clear,
  Enable/Disable bridge, and an explicit two-click optional install. Installs fetch only the official
  release asset and its official `SHA2-256SUMS`, verify SHA-256 before moving the file, set POSIX execute
  bits where supported, support cancellation, and never overwrite an unverified file.
- Structured arguments only: no shell string, cookies, browser stores, login, tokens, impersonation,
  player-client switching, geo/access-control bypass, plugins, config loading or auto-update.
- Extraction is bounded by a configurable timeout, cancellable, and its output is capped. Failures are
  reported honestly; no playback is simulated.
- Because YouTube's terms restrict automated access and use of content outside the service, this bridge is
  unofficial and **ToS-gray**. Use it only where you have the right to access the content.

### Spotify (official Web API)

- Connect from the Spotify tab with a user-registered public Client ID in `config/musicallymods-auth.json`.
  No client secret is bundled or embedded. Register `http://127.0.0.1` as a redirect URI; Spotify permits
  dynamically assigned ports for registered loopback IP literals.
- Official Authorization Code + PKCE opens only after a second confirmation click; startup reconnect is
  silent and never opens a browser.
- Metadata search, artwork, profile validation, token refresh and Spotify Connect device listing/control
  are real. Raw audio is not exposed to Minecraft; an active, unrestricted Premium device is required for
  Connect control.

### SoundCloud (official developer API)

- Connect from the SoundCloud tab only with your own registered Client ID and Client Secret and an Artist
  Pro subscription. Register the exact redirect URI `http://127.0.0.1:8080`.
- Official OAuth 2.1 + PKCE is used; all SoundCloud clients are confidential, so the secret stays in the
  local auth file and is never bundled.
- Search lists only tracks whose official `access` field is `playable`, with creator and SoundCloud
  attribution. Official HLS transcodes cannot be decoded by this mod's Java audio stack, so in-game
  playback is explicitly unavailable. The tab can open a result on SoundCloud after a user click.

### LRCLIB lyrics

- Anonymous, key-less lookup for tracks with real metadata (currently Deezer results).
- **Synced highlighting is enabled only when LRCLIB returns real LRC timestamps.**
- Plain lyrics are shown as **Unsynced lyrics** and are never highlighted as if timed.
- Live stations and direct URLs have no song-level lyrics; the mod says so rather than guessing.

### Integration limits (by design)

| Service | Explicit status |
| --- | --- |
| Spotify | **Official metadata + external player only.** User OAuth is required; in-game audio is never exposed. Premium and an unrestricted active Connect device are required for playback control. |
| SoundCloud | **Official metadata only.** Registered developer credentials and Artist Pro are required; in-game HLS playback is unavailable. |
| YouTube Music | **Opt-in external bridge only.** No official API or Google authentication exists here; the bridge stays off until you enable it and supply or explicitly install an executable. |

These entries appear in the service selector with their integration level, playback support and authentication requirement, so limitations are visible and no button is a no-op.

## Features

- Asynchronous, bounded search and metadata loading with timeouts and human-readable errors.
- One authoritative playback state machine: `IDLE`, `LOADING`, `BUFFERING`, `PLAYING`, `PAUSED`, `STOPPED`, `FAILED`.
  No contradictory playback booleans, no fake playback, bounded retry, and real failures shown in the GUI.
- Streaming playback with play/pause, stop, previous/next, volume and progress; output lines and streams are
  released on every terminal path, and stalled streams fail instead of hanging forever.
- Artwork download and bounded texture cache.
- Six-tab GUI: Home, YouTube Music, Spotify, SoundCloud, Lyrics and Settings. Each platform page has
  its own state, brand text, connection status, redacted errors, clipped scrolling, and only the actions
  that provider really supports.
- Player GUI with fixed header/footer, independently scrolling results, clipped/scissored content,
  scrollbars, stable navigation, and half-open hit regions so adjacent controls never overlap.
- Settings GUI with theme presets, per-color ARGB editor, and service-specific integration/auth/playback status.
- Independent, clipped lyrics overlay registered through Fabric's modern `HudElementRegistry`.
- Persistent schema-`2` JSON config: `config/musicallymods.json`. Older files are read field-by-field with
  safe defaults; every UI, service, playback, integration, lyrics, keybind, color, volume and audio field is validated.
- Corrupt or invalid config fields fall back individually; writes use a temporary file and atomic move when supported.

## Keybinds (defaults)

| Action | Default |
| --- | --- |
| Open/close player | `M` |
| Toggle lyrics HUD | `K` |
| Play/pause | `P` |
| Next track/station | `N` |
| Previous track/station | `B` |

All bindings can be changed in **Minecraft → Options → Controls → MusicallyMods**.

## Configuration

`config/musicallymods.json` is created on first run. The Settings screen edits:

- selected service;
- theme preset and all GUI/lyrics colors;
- lyrics enable, position, alignment, manual X/Y, width, height, font size, scale, padding, line spacing, max lines and transparency;
- title/artist/artwork visibility;
- confirmation-protected reset to defaults.

Additional sections written by the mod (schema `2`):

```json
{
  "schemaVersion": 2,
  "playback": {
    "maxRetryAttempts": 2,
    "requestTimeoutSeconds": 20,
    "useMasterGain": true
  },
  "integrations": {
    "youTubeMusicBridgeEnabled": false,
    "youTubeMusicBridgeExecutable": ""
  },
  "ui": {
    "resultRowHeight": 40,
    "showScrollbars": true
  }
}
```

Keybind names in the file use Minecraft key names such as `key.keyboard.m`; they are bounded and syntax-checked on load, but should be edited in Controls rather than by hand.

### Local session storage

OAuth client configuration and sessions are stored separately in
`config/musicallymods-auth.json`, never in `musicallymods.json`. Local storage is disabled until you
explicitly confirm it in a platform page. The file is written atomically with owner-only permissions on
POSIX file systems; OS keychain/credential-manager integration is not available in this mod's dependency
set, so keep this file private and exclude it from config backups. Disconnect clears only that provider's
session (and revokes SoundCloud grants through the official disconnect endpoint when possible). Session
values, client secrets, authorization codes and stack traces are never logged or shown raw in the GUI.

There is no MusicallyMods account, registration, backend, cloud database or user server.

## Build and install

For end users or maintainers with a normal toolchain:

```bash
./gradlew build
```

Place the produced client JAR in `mods/` alongside Fabric API for Minecraft 1.21.11.

The `0.2.1` release JAR was verified after `./gradlew build`: correct `fabric.mod.json` entrypoint and dependency ranges, bundled audio decoder JARs under `META-INF/jars/`, exact official icon (SHA-256 `0ff25b5f…fc11dd`), and Java 21 bytecode (class major 65). Minecraft itself was not launched.

## Audio dependencies

Bundled pure-Java Java Sound SPI decoders are declared in `build.gradle` under `dev.mccue` version `2024.04.19`:

- `mp3spi`, `jlayer-decoder`, `tritonus-share` for MP3;
- `vorbisspi`, `jogg`, `jorbis` for OGG Vorbis.

They are LGPL-2.1 licensed. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and [THIRD_PARTY_LICENSES/LGPL-2.1.txt](THIRD_PARTY_LICENSES/LGPL-2.1.txt).

## Icon

The exact official icon is bundled unchanged at `assets/musicallymods/icon.png`:

`https://raw.githubusercontent.com/RyzzXCODING/MusicallyMods-icon/main/MusicallyMods_128x128_no_play(1).png`

- 128×128 PNG
- SHA-256: `0ff25b5f691b8ae25883b64f2816dcb4e7461d10ba47ccef656ff92a16fc11dd`

## Credits and license

See [CREDITS.md](CREDITS.md). MusicallyMods source is MIT licensed; see [LICENSE](LICENSE).
