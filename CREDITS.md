# Credits

## Author

- Alpoyy

## Icon

- Official MusicallyMods icon: `MusicallyMods_128x128_no_play(1).png`
- Repository: https://github.com/RyzzXCODING/MusicallyMods-icon
- Raw source: https://raw.githubusercontent.com/RyzzXCODING/MusicallyMods-icon/main/MusicallyMods_128x128_no_play(1).png
- Bundled unchanged at `assets/musicallymods/icon.png` (SHA-256 `0ff25b5f691b8ae25883b64f2816dcb4e7461d10ba47ccef656ff92a16fc11dd`).

## Services and data sources

- Deezer public API — search metadata and official 30-second previews.
- Radio Browser / radio-browser.info — free station directory (AGPL-3.0 service).
- LRCLIB — anonymous community lyrics database.
- Optional YouTube Music bridge: uses a `yt-dlp` executable installed by the user, if they enable it.
  yt-dlp is a separate third-party project; no executable is bundled or downloaded by MusicallyMods.
- Track/station artwork is served by the corresponding service and cached locally at runtime.

## Toolchain

- Minecraft (Mojang)
- Fabric Loader, Fabric Loom, Fabric API (FabricMC)
- Gson (Google), available in the Minecraft/Fabric runtime

## Bundled audio decoders

The following `dev.mccue` `2024.04.19` artifacts are bundled as pure-Java Java Sound SPI providers:

- `mp3spi`
- `jlayer-decoder`
- `tritonus-share`
- `vorbisspi`
- `jogg`
- `jorbis`

They are licensed under the GNU Lesser General Public License, version 2.1.
See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and [THIRD_PARTY_LICENSES/LGPL-2.1.txt](THIRD_PARTY_LICENSES/LGPL-2.1.txt).

## Notes

No Spotify, SoundCloud, or YouTube Music credentials, cookies, tokens, or subscription/DRM/access-control
bypasses are included. Spotify and SoundCloud are explicitly not integrated. YouTube Music is only an
opt-in bridge to a user-installed executable, disabled by default and documented as unofficial and ToS-gray.
