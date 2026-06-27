# CLAUDE.md — minecraft-mod-blind-deaf-muted

> **Note to Claude:** You may write to and update this file as the project evolves.
> Keep it as a living summary. When decisions change or new ones are made, update
> the relevant section here (and `DESIGN.md`) so this stays an accurate, current
> snapshot. Always look forward to improvements — flag risks, better approaches,
> and open questions as they come up.

## What this project is

A cooperative Minecraft challenge mod (working title **"Blind Deaf Muted"**). 3+ friends are
each assigned a **disability** — **blind**, **deaf**, or **muted** — and must
communicate around their limitations to **beat the Ender Dragon**, hardcore or normal.
Admins can move players between roles live via commands. Random events planned later.

Full detail lives in **`DESIGN.md`** (read it for the complete picture).

## Decisions made so far

- **Architecture:** logically two roles — **server logic** (assigns roles, commands,
  game logic) + **client effects** (applies blind/deaf/mute locally, driven by the
  server via a small custom packet) — but shipped as **ONE unified jar** (see the
  "Single unified jar" decision below). Fabric runs the `main`/`voicechat`
  entrypoints on any side and the `client` entrypoint only on a physical client.
- **Effects are client-side** (not server-enforced): makes "deaf" complete & clean,
  avoids server performance issues, keeps shader compatibility.
- **Loader: Fabric** for both pieces (latest-version + shader/Sodium/Iris friendly).
  Backed by a 2026 comparison: NeoForge now leads _content modpacks_, Forge is
  legacy, but Fabric remains best for a _small custom mod_ needing shader compat +
  fast version updates. See `DESIGN.md` §2b.
- **Server runs in Docker** (Fabric server container, e.g. `itzg/minecraft-server`
  with `TYPE=FABRIC`; mod jar in the mounted `mods/` volume). Clients still install
  the thin client jar locally. See `DESIGN.md` §2c.
- **Monorepo layout:** `common/` (shared role enum + packets + version) + `mod/`
  (the single shippable jar: client effects AND server logic) + `docker/`, single
  `gradle.properties` as source of truth. `common` is bundled into `mod` via loom
  `include` (jar-in-jar), so the one published file is self-contained. See
  `DESIGN.md` §2d–2e.
- **Single unified jar (decided & implemented):** client + server were merged into
  one `mod/` module producing `blind-deaf-muted-<version>.jar`. Reason: **Modrinth's
  client/server environment flag is per-PROJECT, not per-file**, so two jars in one
  project can't be tagged correctly (and a zip-of-jars isn't installable). One jar
  with `environment: "*"` and three entrypoints (`main` + `client` + `voicechat`)
  sidesteps this — players install it, the Docker server runs the same file.
  Critical detail: shared registrations (items/entities + S2C payload types) live
  ONLY in the `main` entrypoint (runs on both sides, before `client`); the `client`
  entrypoint does only client-only work (receivers, renderers, keybinds). The
  client mixin config is marked `"environment": "client"` so a dedicated server
  never loads client classes. `ModItems/ModEntities.register()` are also idempotent
  as insurance. (Pre-merge there were separate `client/` + `server/` modules.)
- **Dockerfile:** multi-stage — build the unified jar with `gradle :mod:remapJar`,
  then `COPY` `mod/build/libs/blind-deaf-muted-*.jar` into `/mods` of
  `itzg/minecraft-server` (`TYPE=FABRIC`). `docker compose up` = one command, fully
  self-contained.
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
- **In-game voice: Simple Voice Chat — decided & implemented.** Integrated via its
  server plugin API (`server/BlindDeafMutedVoicechatPlugin.java`); we _enforce_ DEAF (cancel
  inbound sound packets) and MUTED (cancel the speaker's mic packet) server-side, so
  in-game voice is truly enforced, not honor-system. Optional soft-dependency
  (`voicechat` entrypoint + `suggests`); server auto-downloads it + opens UDP 24454,
  every client installs the SVC mod. See `DESIGN.md` §4 and `DEVELOPER.md`.

## Hard constraints

- Latest Minecraft version if possible.
- No conflict with shaderpacks or other client mods.
- Easy to manage (commands to switch roles).
- Hardcore or normal supported.

## Status

**Builds clean.** Both modules compile and produce one shippable jar
(`mod/build/libs/blind-deaf-muted-0.1.0.jar`, with `common-0.1.0.jar` nested via
jar-in-jar) via `./gradlew :mod:build`. On Windows: set `JAVA_HOME` to the
Adoptium JDK 21 and run `.\gradlew.bat :mod:build --no-daemon`.

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

All gameplay code lives in the single `mod/` module (packages keep the logical
split: `com.blinddeafmuted.server.*` and `com.blinddeafmuted.client.*`); `common/`
is the shared library bundled in via jar-in-jar.

- `common/` — `Role` enum, `RolePayload` (S2C packet), `ModConstants` (protocol version).
- `mod/` (`…server.*` package) — `BlindDeafMutedServer` (`main` entrypoint), `RoleManager`
  (in-memory store + sync), `BlindDeafMutedCommand` (`/bdm set <player> <role>`, op-only),
  `BlindDeafMutedVoicechatPlugin` (`voicechat` entrypoint). Shared item/entity + payload
  registration happens here.
- `mod/` (`…client.*` package) — `BlindDeafMutedClient` (`client` entrypoint + packet
  receivers), `RoleState`, and effect handlers `BlindOverlay` / `DeafHandler` / `MuteHandler`
  (functional stubs w/ TODOs). HUD: `TrackerHud` (teammate tracker, key `K`) + `RosterHud`
  (who-is-what leaderboard top-right, key `L`), both drawn from `InGameHudMixin` TAIL.

### Recent additions (idea pass 1)

- **Blind arm pose:** `PlayerEntityModelMixin` overrides the BLIND player's left-arm
  rotation (`setAngles` TAIL) to hold it out forward (`BLIND_LEFT_ARM_PITCH ≈ -1.2`),
  so they sweep the cane ahead of them. Client-side cosmetic; the cane follows the arm
  automatically. Tweakable constant.
- **Blind cane accessory (idea #2, first one):** `BlindCaneFeatureRenderer` draws a
  real 3D cane (a `ModelPart` cuboid 1×24×1 px, NOT a flat texture) in a blind
  player's LEFT hand, visible to everyone. Registered on all player renderers via
  Fabric `LivingEntityFeatureRendererRegistrationCallback`; draws per-player by
  looking up the role from the roster (`RosterState.roleOf(state.name)`). Texture:
  `assets/blind-deaf-muted/textures/entity/blind_cane.png` (16×32, base = white shaft + red
  tip, editable; UV = 4 side strips at x0-7/y2-25, caps at top). Hand position/angle
  are constants (`HAND_DOWN`, `FORWARD_TILT_DEGREES`) — **may need visual tuning**.
  This establishes the pattern for the other accessories (glasses/bandage/hard-hat →
  attach to `head` instead of `leftArm`).
- **Head accessories (idea #2, rest):** `RoleHeadAccessoryFeatureRenderer` — one
  renderer, switches on role: BLIND → dark glasses (2 lenses + bridge), MUTED → beige
  plaster in an X (two strips rotated ±45° over the mouth), DEAF → orange hard-hat
  (dome + front brim). All real 3D cuboids attached to `head`; role looked up from the
  roster. Base textures are flat colours (`glasses/bandage/hard_hat.png`, repaintable);
  cuboid coords are in head space (x[-4,4] y[-8,0] z[-4,4], face on -Z) so positions
  are easy to tweak. **All accessory positions likely need visual tuning.**
- **Tight blind fog (VANILLA mode):** `BackgroundRendererMixin` uses MixinExtras
  `@ModifyReturnValue` on `BackgroundRenderer.applyFog` to rewrite the returned `Fog`
  to a ~2-block start/end while the local player is BLIND in VANILLA mode (so you only
  see your feet). Gated on our blind state+mode; BLACKOUT mode and other blindness
  sources untouched. Tweak `BLIND_FOG_END` in the mixin.
- **Role colours + "You're now …" message (idea #1):** each `Role` now carries a
  Minecraft `Formatting` colour + a `label()` (BLIND=`RED`, DEAF=`GOLD`,
  MUTED=`LIGHT_PURPLE`, NONE=`GRAY`; `AQUA` reserved for a future INVISIBLE).
  `RoleManager.set()` now sends the player a coloured "You're now BLIND" chat line
  (and a "cleared" line for NONE), so every assignment path (set/random/future
  bottle) announces automatically. Admin command feedback is coloured too.
- **Who-is-what leaderboard (idea #4):** new `RosterPayload` (S2C) broadcast once/sec
  from `BlindDeafMutedServer`; `RosterState` + `RosterHud` draw a right-aligned roster using
  the role colours (self shown bold). Protocol bumped to **v3**. Refinements: the
  roster is **frozen during the roulette** (shows old roles, flips to new exactly at
  the reveal — anti-spoiler) and is **visible even when blind** (out-of-character meta
  info; renders white-on-black over the blackout). The teammate tracker stays hidden
  while blind.
- **Roulette reveal animation (idea #4 bis):** `/bdm random` now calls
  `RoleManager.setAnimated()` → sends a new `RollPayload` (S2C) instead of applying
  instantly. Client `RouletteAnimation` spins a slot machine through the roles
  (ease-out, lands on the rolled role), holds "You're now X", then applies the effect
  at the reveal (so a blind player watches their own roll before blacking out). The
  role is still stored server-side immediately (roster + voice enforcement correct
  right away). Manual `/bdm set` stays instant (no animation). Protocol → **v4**.
  Sounds: rising-pitch `UI_BUTTON_CLICK` per reel step + `UI_TOAST_CHALLENGE_COMPLETE`
  fanfare at the reveal.
- **Randomizer bottle (idea #3):** a throwable item (`ModItems.RANDOMIZER`) +
  thrown entity (`RandomizerBottleEntity`, modelled on the XP bottle) registered in
  `common` and called from BOTH entrypoints (same ids both sides). On shatter it
  re-rolls EVERY online player via `RoleRoller.rollAll` → `setAnimated`, so the
  roulette plays for everyone. Server logic is injected into a static
  `RandomizerBottleEntity.SHATTER_HANDLER` (common can't see the `server` module).
  Lootable in structure chests via `LootTableEvents.MODIFY` (10% in dungeon/mineshaft/
  weaponsmith/stronghold/jungle/desert/nether-bridge/bastion-treasure) — works on
  already-generated worlds since chests only roll loot on first open. Test helper:
  `/bdm randomizer` gives 4 bottles. Item renders as the vanilla XP-bottle texture
  (no PNG shipped); 1.21.4 item-model defs under `assets/blind-deaf-muted/items|models`.
  No protocol change (reuses RollPayload). `RoleRoller` also de-duplicates the random
  logic that used to live in `BlindDeafMutedCommand`.
- **Skin (accessory) visibility toggle:** `/bdm skin <on|off>` (op-only) hides/shows
  the mod's custom role accessories — the blind cane/glasses, muted bandage, deaf
  headset — for everyone (it does NOT touch Minecraft player skins). Server owns the
  flag (`SkinVisibilityManager`, on by default) and broadcasts it via a new
  `SkinVisibilityPayload` (S2C `boolean`) riding the once/sec roster tick, so a
  late-joiner syncs within a second. Client mirrors it in `SkinVisibilityState`
  (volatile); both feature renderers (`BlindCaneFeatureRenderer`,
  `RoleHeadAccessoryFeatureRenderer`) early-return in `render()` when disabled.
  Protocol bumped to **v5**. Not persisted across restarts (defaults ON, like the
  shared-health toggle).
- Fixed a pre-existing compile break in `TrackerHud` (undefined `elevation` var left
  by the compass-HUD commit) — now shows a ↑/↓ elevation hint past the threshold.
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
