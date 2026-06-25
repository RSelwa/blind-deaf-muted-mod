# DEVELOPER.md — Monkeys

Developer-facing notes: how the effects are implemented, and a running TODO list.
High-level design lives in `DESIGN.md`; project status in `CLAUDE.md`.

## Reloading the server after a change

The server runs in Docker (`itzg/minecraft-server`, `TYPE=FABRIC`). The mod jar is
**baked into the image** at build time, so there is no hot-reload — to test new code
you rebuild the image and recreate the container. All commands below run from the
`docker/` folder.

| What you changed | Command | Why |
|------------------|---------|-----|
| **Java code** (`server/`, `common/`) | `docker compose up -d --build` | Recompiles the jar and bakes it into a fresh image. **This is the usual one.** |
| **Only `docker-compose.yml`** (env, ports, volumes) | `docker compose up -d --force-recreate` | Runtime config — no rebuild needed; just recreate the container. |
| **Nothing — just restart** | `docker compose restart` | Reboots the same image (e.g. to clear in-memory state). |

Key point: plain `docker compose up -d` will **not** pick up Java changes — it reuses
the existing image. You must pass `--build` after editing code. (`up` only builds when
the image is missing.)

Optional sanity check before rebuilding the image (faster feedback than a full Docker
build, which runs Gradle inside the container):

```bash
# from repo root — confirms it compiles locally first
./gradlew :common:build :server:build      # Windows: .\gradlew.bat ...
```

### Watching / driving the running server

```bash
docker compose logs -f monkeys             # tail the server log (Ctrl-C to stop tailing)
docker compose exec monkeys rcon-cli       # interactive server console (RCON)
docker compose exec monkeys rcon-cli op <name>   # one-off console command, e.g. grant op
```

World data and op status live in the `./world` volume, so they survive rebuilds and
restarts — you only `op` yourself once.

> **Client side:** the client jar is **not** in Docker. After changing client code,
> rebuild it (`./gradlew :client:build`) and reinstall `client/build/libs/client-*.jar`
> in your local Minecraft `mods/` folder, then relaunch the game.

## How each effect is implemented (client side)

| Role | Mechanism | File |
|------|-----------|------|
| **Blind** | Two modes, see below | `BlindHandler`, `mixin/InGameHudMixin`, `BlindMode` |
| **Deaf** | Cancel all sound playback at the source; stop in-flight sounds on transition | `mixin/SoundSystemMixin`, `DeafHandler` |
| **Muted** | Block outgoing chat | `MuteHandler` |

### Blind — two modes (`BlindMode`)
Toggle live with the **`B`** keybind (rebindable; category "Monkeys"). Mode is a
client-side visual style only — the player can't see the environment either way.

- **`BLACKOUT_HUD`** (default) — `InGameHudMixin` injects at the HEAD of
  `InGameHud#render` and fills the screen black *before* the vanilla HUD draws. Result:
  environment hidden, but hotbar/health/hunger/hand + any open screen (inventory) stay
  visible.
- **`VANILLA`** — `BlindHandler` re-applies Minecraft's Blindness status effect to the
  local player every tick (short duration, refreshed; ambient, no particles, no icon).

### Deaf — mod-driven, never touches user settings
- `SoundSystemMixin` cancels `SoundSystem#play` while deaf → nothing new plays.
- `DeafHandler` calls `SoundManager#stopAll()` on the transition into deafness → kills
  currently-playing audio (music, looping ambience).
- We deliberately do **not** modify `client.options` volumes, so moving a player
  between roles is instant and lossless (nothing to restore / desync). Sound resumes
  naturally when deafness ends.

## TODO

### Soon
- [ ] **Muted UX** — show a "🔇 you are MUTED" toast when a chat message is swallowed
      (`MuteHandler` TODO).
- [ ] **Protocol-mismatch UX** — surface the version-mismatch warning to the player as
      an on-screen toast instead of only a log line (`MonkeysClient#handleRole`).
- [ ] **Persist roles** — `RoleManager` is in-memory only; roles are lost on server
      restart. Persist to world save / player data.
- [ ] **Blind mode selection** — currently a client keybind. Decide whether the admin
      should be able to set/lock the mode server-side (would need a packet field).

### Planned features
- [ ] **Inter-player compass** — give each player a compass that points toward the
      other players (or toward a chosen teammate). Helps the team regroup/relay around
      their disabilities. Likely needs: server tracks player positions → small S2C
      packet (or vanilla lodestone-compass trickery) → client renders the needle.
      Open questions: point to whom (nearest? a fixed buddy? rotate?), and does it
      undercut the "blind can't see / muted can't tell you where I am" tension?
      Design before building.
- [ ] **Random events** (from `DESIGN.md` §7) — bolt on once the role system is solid.

### Known caveats
- **Vanilla blind in multiplayer** — the Blindness effect is applied *client-side*.
  The server owns a player's real status effects, so if we ever apply server-side
  effects to a blind player they could momentarily fight the client-applied one.
  Fine today (we never do); revisit if it flickers in testing.
- **Muted + signs** — sign text is a world edit, not chat, so `MuteHandler` doesn't
  block it. Open decision in `DESIGN.md` §7 (leaning: embrace as the Muted's slow
  "written note" channel).
