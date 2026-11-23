# MusicallyMods

MusicallyMods is a client-side Fabric music hub for Minecraft **1.21.11** (Java **21**). It provides a unified in-game UI to manage supported music providers (scaffolded for **Spotify** and **YouTube Music**) with configurable keybinds and an optional now-playing HUD.

> **Status:** This repository contains a buildable scaffold. Spotify/YouTube Music features that require real OAuth/API calls are implemented as safe stubs until you configure provider credentials and complete integration work. No real playback is claimed beyond what each provider/API allows.

## Requirements

- Minecraft 1.21.11
- Fabric Loader (see `gradle.properties` / `fabric.mod.json`)
- Fabric API
- Java 21

## Build

```bash
./gradlew build
```

Artifacts are written to `build/libs/` (e.g. `musicallymods-0.1.0.jar`).

## Install

Place the built (or released) JAR in your Minecraft `mods` folder for a **client** profile with Fabric Loader + Fabric API.

## Keybinds (default)

- **M** — Open/close MusicallyMods (configurable in Minecraft Controls)
- **Play/Pause**, **Next**, **Previous**, **HUD Toggle** — unbound by default; assign in Controls under the MusicallyMods category

## Configuration

Settings persist to `config/musicallymods.json` (created on first run). Options include HUD toggles/position basics, polling interval, notifications toggle, and default provider id.

## Provider notes (important)

### Spotify
- Full integration should use **OAuth 2.0 Authorization Code + PKCE**.
- **Do not ship client secrets** in the mod. Users/developers must follow Spotify’s developer terms.
- Spotify playback control typically requires an **active Spotify Connect device** and may require **Spotify Premium** depending on endpoint; behavior varies by API. This mod does **not** play audio inside Minecraft.
- This build ships a **scaffold** only.

### YouTube Music
- No official public YouTube Music API for third-party clients in the same way as Spotify; integrations often rely on **unofficial/open-source libraries**. Evaluate licenses, maintenance, security, and ToS carefully.
- **Never** store raw passwords, never hardcode cookies, never bypass DRM or subscription protections, respect rate limits.
- This build ships a **scaffold** only.

## Icon

Official icon (exact file): [`MusicallyMods_128x128_no_play(1).png`](https://raw.githubusercontent.com/RyzzXCODING/MusicallyMods-icon/main/MusicallyMods_128x128_no_play(1).png) from [RyzzXCODING/MusicallyMods-icon](https://github.com/RyzzXCODING/MusicallyMods-icon). The original PNG is bundled unchanged.

## Credits

See [CREDITS.md](CREDITS.md).

## License

MIT (see [LICENSE](LICENSE)).
