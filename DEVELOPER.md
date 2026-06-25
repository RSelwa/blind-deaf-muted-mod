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

Deaf/Muted are *also* enforced over **voice** (server side) — see the Voice section below.

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

## Voice chat (Simple Voice Chat integration — server side)

We don't build voice ourselves; we integrate **[Simple Voice Chat](https://modrepo.de/minecraft/voicechat/api)**
(henkelmax) and enforce roles through its **server plugin API**. Because all voice
is relayed through the server, cancelling packets there enforces the rules for
everyone with no client cooperation — this is what makes DEAF/MUTED *enforced* over
voice (not just honor-system as the original `DESIGN.md` note assumed).

- **File:** `server/MonkeysVoicechatPlugin.java` (a `VoicechatPlugin`).
- **Registration:** the `voicechat` entrypoint in `server/fabric.mod.json`. Only the
  voice-chat mod reads that key, so if it isn't installed the class is never loaded —
  that's why SVC stays an **optional soft dependency** (`suggests`, not `depends`).
- **MUTED** → cancel the speaker's `MicrophonePacketEvent` (mic dropped at the server
  before it reaches anyone).
- **DEAF** → cancel `Entity`/`Locational`/`Static` `SoundPacketEvent` whose *receiver*
  is deaf (covers proximity, group, entity, spectator audio).
- **Role lookup:** `RoleManager.get(UUID)` — the voice connection only exposes a UUID,
  not a `ServerPlayerEntity`. The plugin gets the `RoleManager` via the static
  `MonkeysVoicechatPlugin.bind(...)` call in `MonkeysServer#onInitialize` (SVC builds
  the plugin itself, so we can't constructor-inject it).

### Build & deploy notes
- `voicechat-api` is a **`compileOnly`** dependency (`gradle.properties`
  `voicechat_api_version`, repo `maven.maxhenkel.de`). It is *not* bundled — the real
  classes come from the installed mod at runtime.
- **Server:** `docker-compose.yml` auto-downloads `simple-voice-chat` from Modrinth and
  opens **UDP 24454** (the voice port). Without that UDP port, players connect but voice
  silently never starts.
- **Clients:** every player must install the Simple Voice Chat mod (1.21.4 build) in
  their own `mods/` folder, same as Fabric API.

## Teammate tracker (HUD)

A co-op QoL aid so players don't get hopelessly lost: a small stack of lines just
above the health/food bar, one per teammate — `Name  142b  ↗` (name, distance in
blocks, direction arrow relative to where you're looking, `↑` = dead ahead; a `▲`/`▼`
is appended when the teammate is well above/below you).

- **On by default**, toggled with a keybind (default **`K`**, category "Monkeys").
  State lives in `TrackerState` (`enabled` boolean + latest positions).
- **Why server-driven:** a client only knows positions of *loaded* players, so the
  server pushes everyone's positions to everyone via `TrackerPayload` (`:common`),
  every `TRACKER_INTERVAL_TICKS` (5 ticks ≈ 4×/sec) from `MonkeysServer`. Bumped
  `PROTOCOL_VERSION` → 2.
- **Render:** `TrackerHud.render()`, called from `InGameHudMixin` at the **TAIL** of
  `InGameHud#render` (draws on top of the finished HUD). Direction math:
  `atan2(-dx, dz)` for the target yaw, minus the player yaw, into 8 arrow sectors.
- **Role-gated:** never drawn while **BLIND** (can't see the HUD anyway), and respects
  F1 (`options.hudHidden`). Deaf/Muted/None all see it.
- **Open design lever:** currently shows *all* teammates. If full awareness flattens
  the relay tension, switch to "assigned buddy only" — the packet/render already
  supports any subset; just filter server-side in `broadcastTrackerPositions`.

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
- [x] **Inter-player tracker** — done, shipped as the **HUD teammate tracker** above
      (name · distance · arrow) rather than a compass item. Open question "point to
      whom?" resolved to "all teammates, default-on" for now; revisit if it flattens
      the relay tension (see the design lever note in that section).
- [ ] **Random events** (from `DESIGN.md` §7) — bolt on once the role system is solid.
- [ ] **Persist tracker toggle** — the `K` on/off state is in-memory; resets each
      launch. Persist to a small client config if players want it to stick.

### Known caveats
- **Vanilla blind in multiplayer** — the Blindness effect is applied *client-side*.
  The server owns a player's real status effects, so if we ever apply server-side
  effects to a blind player they could momentarily fight the client-applied one.
  Fine today (we never do); revisit if it flickers in testing.
- **Muted + signs** — sign text is a world edit, not chat, so `MuteHandler` doesn't
  block it. Open decision in `DESIGN.md` §7 (leaning: embrace as the Muted's slow
  "written note" channel).
