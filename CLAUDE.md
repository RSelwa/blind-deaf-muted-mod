# CLAUDE.md — minecraft-mod-monkeys

> **Note to Claude:** You may write to and update this file as the project evolves.
> Keep it as a living summary. When decisions change or new ones are made, update
> the relevant section here (and `DESIGN.md`) so this stays an accurate, current
> snapshot. Always look forward to improvements — flag risks, better approaches,
> and open questions as they come up.

## What this project is

A cooperative Minecraft challenge mod (working title **"Monkeys"**). 3+ friends are
each assigned a **disability** — **blind**, **deaf**, or **muted** — and must
communicate around their limitations to **beat the Ender Dragon**, hardcore or normal.
Admins can move players between roles live via commands. Random events planned later.

Full detail lives in **`DESIGN.md`** (read it for the complete picture).

## Decisions made so far

- **Architecture:** two pieces — a **server component** (assigns roles, commands,
  game logic) + a **thin Fabric client mod** (applies blind/deaf/mute locally,
  driven by the server via a small custom packet).
- **Effects are client-side** (not server-enforced): makes "deaf" complete & clean,
  avoids server performance issues, keeps shader compatibility.
- **Loader: Fabric** for both pieces (latest-version + shader/Sodium/Iris friendly).
  Backed by a 2026 comparison: NeoForge now leads *content modpacks*, Forge is
  legacy, but Fabric remains best for a *small custom mod* needing shader compat +
  fast version updates. See `DESIGN.md` §2b.
- **Server runs in Docker** (Fabric server container, e.g. `itzg/minecraft-server`
  with `TYPE=FABRIC`; mod jar in the mounted `mods/` volume). Clients still install
  the thin client jar locally. See `DESIGN.md` §2c.
- **Monorepo layout:** `common/` (shared role enum + packet + version), `client/`,
  `server/`, `docker/`, single `gradle.properties` as source of truth. `common`
  keeps client/server versions in lockstep. See `DESIGN.md` §2d–2e.
- **Dockerfile:** multi-stage — build server jar with Gradle, then `COPY` it into
  `/mods` of `itzg/minecraft-server` (`TYPE=FABRIC`). `docker compose up` = one
  command, fully self-contained.
- **Version sync** (main friction risk): single source of truth + release jars as a
  pair + version handshake on join. See `DESIGN.md` §2f.
- **Who does what:** host = the server (one command, no separate setup); players
  install Fabric + client jar; networking (port-forward/tunnel/VPS) is the only
  non-automatic part and is inherent to any MC server. See `DESIGN.md` §2g.
- **Lowest-friction delivery:** CI publishes a prebuilt server image to GHCR so the
  host just references it in `docker-compose.yml` (no build). Future option: ship
  client as a Modrinth modpack for one-click install.
- **Cost accepted:** one shared client jar must be installed (was originally hoping
  for zero install; client-side is the better trade).
- **Comms flexibility:** config toggle. Proximity voice optional (soft-dependency);
  Discord fallback supported. Note: deaf/muted are only fully enforceable for
  in-game comms; over Discord they become honor-system + soft HUD cues. Blind is
  always enforceable.

## Hard constraints

- Latest Minecraft version if possible.
- No conflict with shaderpacks or other client mods.
- Easy to manage (commands to switch roles).
- Hardcore or normal supported.

## Status

**Builds clean.** All three modules compile and produce jars
(`{common,server,client}/build/libs/*-0.1.0.jar`) via
`./gradlew :common:build :server:build :client:build`.

Toolchain: Gradle wrapper pinned to **8.12** (fabric-loom 1.9.2 doesn't support
Gradle 9), Java **21** (`org.gradle.java.installations.paths` in `gradle.properties`
points at Homebrew `openjdk@21`). Run builds with
`JAVA_HOME=/opt/homebrew/opt/openjdk@21/.../Home ./gradlew ...`.

Fabric strings verified against the Fabric meta API for **1.21.4**: yarn
`1.21.4+build.8`, loader `0.16.10`, fabric-api bumped to `0.119.4+1.21.4`.

Compile fixes applied: `SoundCategory` import was `net.minecraft.client.option`
→ corrected to `net.minecraft.sound`; client entrypoint method `onInitialize()`
→ `onInitializeClient()` (the `ClientModInitializer` contract).

Monorepo structure + skeleton code (effect handlers are functional stubs w/ TODOs):
- `common/` — `Role` enum, `RolePayload` (S2C packet), `ModConstants` (protocol version).
- `server/` — `MonkeysServer` (entrypoint), `RoleManager` (in-memory store + sync),
  `MonkeysCommand` (`/monkeys set <player> <role>`, op-only).
- `client/` — `MonkeysClient` (entrypoint + packet receiver), `RoleState`, and effect
  handlers `BlindOverlay` / `DeafHandler` / `MuteHandler` (functional stubs w/ TODOs).
- `docker/` — multi-stage `Dockerfile` + `docker-compose.yml`.
- Root Gradle multi-project (`settings.gradle`, `build.gradle`, `gradle.properties`).

**Comments ARE allowed in this project** (user opted in) — overrides the global
no-comments rule.

### Must-verify before first build
- Fabric version strings in `gradle.properties` (minecraft/yarn/loader/fabric-api)
  against https://fabricmc.net/develop — they must match exactly.
- No Gradle wrapper committed yet — run `gradle wrapper` (or rely on system Gradle).
- `RolePayload` uses the 1.20.5+ CustomPayload/PacketCodec API; re-check if version changes.
- `common` is bundled into client/server via loom `include` (jar-in-jar) — confirm at build.

## Next up

- ~~Design the **role/communication loop**~~ — done, see `DESIGN.md` §4b
  (channel matrix: each role missing one of See/Hear/Speak → forced relay triangle).
- Decide the **Muted + signs** question (`DESIGN.md` §7).
- Flesh out the effect-handler TODOs (DeafHandler volume snapshot/restore,
  MUTED toast, blind hotbar question).
- Random events.
- Command syntax & name finalization.

## Reference

- "Completely Blind" mod (inspiration only, not reusable — client-only, no server
  control): <https://www.curseforge.com/minecraft/mc-mods/completely-blind>
