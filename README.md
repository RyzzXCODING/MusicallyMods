# MusicallyMods

MusicallyMods is a **client-only** Fabric mod for Minecraft **1.21.11** (Java **21**) that provides a lightweight in-game music player, asynchronous search, real playback where the source legally permits it, artwork, and a customizable independent lyrics HUD.

> **Honest scope:** this mod does not include Spotify, YouTube Music or SoundCloud accounts, tokens, cookies, or DRM/access-control circumvention. Spotify and SoundCloud are shown as **not integrated** with the specific reason in the GUI. YouTube Music is an **opt-in, disabled-by-default** bridge that calls a user-installed `yt-dlp` executable; no executable is bundled or downloaded. It is unofficial, fragile and may conflict with YouTube's Terms of Service.

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
- Requires a `yt-dlp` executable that **you install yourself**, found on `PATH` or set as an absolute path in
  `integrations.youTubeMusicBridgeExecutable`. Nothing is downloaded or bundled by the mod.
- Structured arguments only: no shell string, cookies, browser stores, login, tokens, impersonation,
  player-client switching, geo/access-control bypass, plugins, config loading or auto-update.
- Extraction is bounded by a configurable timeout, cancellable, and its output is capped. Failures are
  reported honestly; no playback is simulated.
- Because YouTube's terms restrict automated access and use of content outside the service, this bridge is
  unofficial and **ToS-gray**. Use it only where you have the right to access the content.

### LRCLIB lyrics

- Anonymous, key-less lookup for tracks with real metadata (currently Deezer results).
- **Synced highlighting is enabled only when LRCLIB returns real LRC timestamps.**
- Plain lyrics are shown as **Unsynced lyrics** and are never highlighted as if timed.
- Live stations and direct URLs have no song-level lyrics; the mod says so rather than guessing.

### Not integrated (by design)

| Service | Explicit status |
| --- | --- |
| Spotify | **Not integrated.** Official Web API only. Metadata/search would require user OAuth via Authorization Code with PKCE and a user-registered Client ID; no client secret, token or cookie is bundled. In-game audio is never exposed. Playback control is Premium-only, needs `user-modify-playback-state` and an active, unrestricted Spotify Connect device; Free accounts and restricted devices cannot be controlled. |
| SoundCloud | **Not integrated.** Developer authorization/approval is required; no bundled client ID, user token, cookie or Premium bypass is included. No search or playback is offered. |
| YouTube Music | **Not integrated by default.** The optional external `yt-dlp` bridge stays off until you enable it and supply your own executable. It is metadata/search plus direct stream extraction, not an official API or support. |

These entries appear in the service selector with their integration level, playback support and authentication requirement, so limitations are visible and no button is a no-op.

## Features

- Asynchronous, bounded search and metadata loading with timeouts and human-readable errors.
- One authoritative playback state machine: `IDLE`, `LOADING`, `BUFFERING`, `PLAYING`, `PAUSED`, `STOPPED`, `FAILED`.
  No contradictory playback booleans, no fake playback, bounded retry, and real failures shown in the GUI.
- Streaming playback with play/pause, stop, previous/next, volume and progress; output lines and streams are
  released on every terminal path, and stalled streams fail instead of hanging forever.
- Artwork download and bounded texture cache.
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

## Build and install

For end users or maintainers with a normal toolchain:

```bash
./gradlew build
```

Place the produced client JAR in `mods/` alongside Fabric API for Minecraft 1.21.11.

A previously produced release JAR was verified: correct `fabric.mod.json` entrypoint and dependency ranges, bundled audio decoder JARs under `META-INF/jars/`, exact official icon (SHA-256 `0ff25b5f…fc11dd`), and Java 21 bytecode (class major 65). Minecraft was not launched. The source-only audit represented by the current repository state did **not** run any build.

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
