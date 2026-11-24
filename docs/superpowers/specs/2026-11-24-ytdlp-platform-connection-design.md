# yt-dlp Repair and Platform Connection Design

## Intent

Fix the reported `yt-dlp execution not found` failure at its source and add honest, independent
connection pages for YouTube Music, Spotify and SoundCloud without creating a MusicallyMods account
system or backend. Preserve the existing 1.21.11 Fabric player, lyrics HUD, keybinds and config.

## Approaches considered

1. **Reword the error only.** Rejected: it hides the root cause and changes nothing at runtime.
2. **Bundle or auto-install yt-dlp silently.** Rejected: untrusted execution, licensing/security risk,
   and the user requires explicit confirmation and verification.
3. **Repair detection and add a user-controlled manager, then layer official, per-provider auth on top.**
   Chosen: fixes the actual lookup failure, gives the user diagnostics and control, and keeps unsupported
   features honestly unavailable.

## Root cause (source-level, no in-game repro permitted)

`YtDlpProcess.detect` only probes two names (`yt-dlp` / `yt-dlp.exe`), only on `PATH` or one configured
path. Official standalone releases use OS/architecture-specific names (`yt-dlp_linux`,
`yt-dlp_linux_aarch64`, `yt-dlp_linux_armv7l`, `yt-dlp_macos`, `yt-dlp_x86`, `yt-dlp.exe`,
`yt-dlp_win-arm64.exe`), GUI launchers often omit user install directories from the inherited `PATH`,
and downloaded Unix binaries commonly lack the execute bit. All three cases currently collapse into
"executable not found"; there is no version or health check and no mod-managed location. This is the
failure path that produces the reported message. It cannot be reproduced in-game here because the
current task forbids building and launching Minecraft.

## Decisions

- Add `YtDlpManager`: ordered detection (configured path → mod-managed directory → `PATH` → known user
  install dirs), OS/arch-aware candidate names, permission-specific status, version/health check,
  cached diagnostics, async retry, path configuration and confirmed, checksum-verified optional install.
- Keep `YtDlpProcess` as a structured, cancellable `ProcessBuilder` runner; it consumes the manager's
  resolved path. No shell, separate arguments, bounded output/timeouts, descendant cleanup, and no
  duplicate active requests.
- Add one `PlatformConnection` per provider with independent state, errors, settings and auto-reconnect.
- Spotify uses Authorization Code + PKCE with a user-supplied public Client ID and loopback redirect;
  no secret is embedded. Web API provides metadata and Connect-device control only, never raw audio.
- SoundCloud follows current official developer requirements; if confidential credentials or approval
  are required, the page explains exactly what is missing and does not fake a connection.
- YouTube Music remains unauthenticated; it reports bridge readiness, never asks for Google credentials
  and never stores cookies.
- Sessions live in a separate owner-restricted `config/musicallymods-auth.json`, never in the main
  config or logs. OS keychain integration is not available through the existing dependencies; the GUI
  states this limitation and requires explicit consent before persisting a session.
- Startup reconnect validates stored sessions silently; a failure in one provider never logs out another.

## Non-goals

- No MusicallyMods accounts, backend, cloud database, or embedded provider credentials.
- No cookie import, password collection, DRM/access-control bypass, or Premium circumvention.
- No build, compile, remap, JAR generation or in-game test under this task.
