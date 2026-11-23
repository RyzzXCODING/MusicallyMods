# MusicallyMods

MusicallyMods is a **client-only** Fabric mod for Minecraft **1.21.11** (Java **21**) that provides a lightweight in-game music player, asynchronous search, real playback where the source legally permits it, artwork, and a customizable independent lyrics HUD.

> **Honest scope:** this mod does not include Spotify, YouTube Music, or SoundCloud accounts, tokens, cookies, or DRM circumvention. Services that cannot be used legally and anonymously are shown as **not integrated**, with the reason in the GUI.

## Version contract

| Component | Version |
| --- | --- |
| Minecraft | `1.21.11` (exact; never retargeted) |
| Java | `21` |
| Fabric Loader | `0.20.1` |
| Fabric API | `0.141.6+1.21.11` |
| Loom | `1.19.4` (stable `fabric-loom-remap`) |
| Mappings | Official Mojang mappings (`loom.officialMojangMappings()`) |
| Gradle wrapper | `9.2.1` |

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

### LRCLIB lyrics

- Anonymous, key-less lookup for tracks with real metadata (currently Deezer results).
- **Synced highlighting is enabled only when LRCLIB returns real LRC timestamps.**
- Plain lyrics are shown as **Unsynced lyrics** and are never highlighted as if timed.
- Live stations and direct URLs have no song-level lyrics; the mod says so rather than guessing.

### Not integrated (by design)

| Service | Why |
| --- | --- |
| Spotify | Web API needs a user-supplied OAuth app/client ID; full playback control needs an active Spotify Connect device and may require Premium. No credentials are bundled. |
| YouTube Music | No official public third-party playback API; unofficial clients risk Terms-of-Service violations. |
| SoundCloud | Public API now requires developer authorization; no user tokens are bundled. |

These entries appear in the service selector so limitations are visible and no button is a no-op.

## Features

- Asynchronous search and metadata loading with timeouts and human-readable errors.
- Streaming playback with play/pause, stop, previous/next, volume and progress.
- Artwork download and bounded texture cache.
- Player GUI with service capability notes and explicit status.
- Settings GUI with theme presets, per-color ARGB editor, lyrics position/alignment/size/layout options.
- Independent lyrics overlay registered through Fabric's modern `HudElementRegistry`.
- Persistent validated JSON config: `config/musicallymods.json`.
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

Keybind names in the file use Minecraft key names such as `key.keyboard.m`; edit bindings in Controls rather than by hand.

## Build and install

For end users or maintainers with a normal toolchain:

```bash
./gradlew build
```

Place the produced client JAR in `mods/` alongside Fabric API for Minecraft 1.21.11.

> **Verification status for this repository update:** no Gradle build, compilation, packaging, JAR generation, or Minecraft launch was performed. Validation was static only, as requested.

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
