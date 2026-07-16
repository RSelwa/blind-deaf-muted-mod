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
  Protocol bumped to **v5**. Not persisted across restarts (defaults ON).
- **Auto-randomizer timer (idea #5):** `server/RandomEventManager.java` — a server-tick
  timer (off by default) that re-rolls every online player's role every 3–8 min while
  enabled (reuses `RoleRoller.rollAll` → roulette, same as a shattered Randomizer bottle).
  Toggle `/bdm events <on|off>`; `/bdm events now` force-fires a re-roll immediately
  (testing/recording). No protocol change (re-roll uses the existing role-sync path).
  Tunable constants: `MIN/MAX_INTERVAL_TICKS`. (Potion-effect events were considered then
  dropped — the timer is re-roll only.)
- A **Vite + TypeScript + Tailwind v4** showcase site lives in `site/` (French tutorial +
  downloadable jar served from `site/public/downloads/`, kept tracked via a `.gitignore`
  exception). Tailwind via `@tailwindcss/vite` (no config file; theme in `@theme` block in
  `src/style.css`); markup in `src/main.ts`. **Version is read from `gradle.properties`
  at build** (`?raw` import → parse `mod_version`/`minecraft_version`), so no manual
  `MOD_VERSION` bump in the site — just re-copy the jar to `public/downloads/` after a
  release build. `npm run build` runs `tsc` then `vite build`.
  **Copy-paste Docker files:** the tutorial's Étape 3 offers the real Docker files inline
  with **Copier** / **Télécharger** buttons — Option A (recommended, no repo/Git/Java):
  copy `docker-compose.user.yml` (prebuilt GHCR image) → `docker compose up -d`; Option B
  (from source): `docker-compose.yml` + `Dockerfile` → `--build`. File contents pulled
  from `docker/` at build via Vite `?raw` imports (single source of truth, no drift),
  rendered by the `codeFile()` helper; one delegated click listener does clipboard-copy
  with a Blob-download fallback. The GHCR image tag in the copied/downloaded compose is
  **re-pinned to `mod_version`** at build (regex replace on the raw text), so the file
  users get is always aligned with `gradle.properties` regardless of the literal tag in
  the repo `docker-compose.user.yml`. Reads `../../docker/*` + `../../gradle.properties`
  outside `site/` — works in build + dev (repo `.git` = workspace root); if dev ever
  blocks it, add `server.fs.allow: ['..']` to `vite.config.ts`.
- Fixed a pre-existing compile break in `TrackerHud` (undefined `elevation` var left
  by the compass-HUD commit) — now shows a ↑/↓ elevation hint past the threshold.
- `docker/` — multi-stage `Dockerfile` + `docker-compose.yml`.
- Root Gradle multi-project (`settings.gradle`, `build.gradle`, `gradle.properties`).

**Comments ARE allowed in this project** (user opted in) — overrides the global
no-comments rule.

### Recent additions (idea pass 2 — sensory tuning)

- **MYOPIA blind mode (3rd `BlindMode`):** depth-aware blur post-effect. Near blocks
  (~2-3) stay sharp, everything past smears into shapes (see "a wall", not which block).
  Files: `assets/blind-deaf-muted/post_effect/myopia.json` (pipeline, 2-pass separable),
  `shaders/post/myopia.json` (program def, reuses vanilla `minecraft:post/blur` vsh),
  `shaders/post/myopia.fsh` (per-fragment blur radius from linearized depth). Driven by
  `MyopiaController` (client tick) which installs/clears the processor via
  `GameRendererAccessor` (`@Invoker setPostProcessor`). VANILLA (fog) + BLACKOUT_HUD kept;
  cycle all three with `B`. **Blur falloff constants (SHARP_BLOCKS / FULL_BLUR_BLOCKS /
  MAX_TEXEL_RADIUS) need in-game calibration** — not visually tested yet.
- **Voice rebalance (`VoiceFx`):**
  - MUTED + megaphone → *lighter* garble (KEEP_BITS 9 / no downsample) + moderate gain:
    vaguely intelligible if they speak slowly, still hard. Without megaphone unchanged
    (heavy garble + faint = unintelligible).
  - DEAF, non-muted speaker, no megaphone → amplified + white noise + hard-clip: audible
    but a crunchy mess. Muted speaker stays faint+muffled (mic already garbled at source).
    Megaphone still overrides both (loud + clear). Speaker role passed via
    `forDeaf(..., speakerMuted)` from the voicechat plugin.
- **Build note (macOS):** `gradle.properties` pins a Windows JDK path; on mac build with
  `JAVA_HOME=/opt/homebrew/opt/openjdk@21/.../Home ./gradlew :mod:build --no-daemon
  -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@21/.../Home`.

### Recent additions (live-tuning slider menu)

- **In-game settings menu (no restart, no config file editing):** all the sensory
  tunables that used to be `static final` constants are now one live `ModConfig`
  record in `common/` (14 fields: deaf/muted voice cutoffs + volumes, deaf+megaphone
  and muted+megaphone variants, blind fog HARD/MEDIUM ends, deaf ambient volume,
  auto-reroll min/max minutes, randomizer chest chance). Press **`O`** to open
  `ConfigScreen` — a 2-column slider menu (`SliderWidget` per knob, value formatted
  per style: Hz / ×gain / blocks / min / %). Reset-defaults + Done buttons.
- **Server = source of truth; clients listen.** `ConfigManager` (server) holds the
  live `ModConfig`, persists it to `config/blind-deaf-muted.json` (Gson, tolerant of
  missing keys), broadcasts `ConfigPayload` (S2C) on join + after every change.
  A slider edit sends `ConfigUpdatePayload` (C2S) → server stores+persists+re-broadcasts.
  Protocol bumped to **v7**. **Access is open to everyone** (no op gate — cheat risk
  accepted per the user).
- **Live application, no restart:**
  - `VoiceFx` (server-enforced deaf/muted audio) reads cutoffs/volumes from a
    `Supplier<ModConfig>` (bound via `BlindDeafMutedVoicechatPlugin.bindConfig`) and
    recomputes the one-pole `lowpassAlpha` inline each 20 ms frame — a slider change
    lands on the next voice frame. The comedic bystander-bullhorn shaping stays a
    hard-coded constant (nobody asked to tune it).
  - Client vision knobs (`BackgroundRendererMixin` fog ends, `SoundSystemMixin` deaf
    ambient volume) read `ClientConfigState` (the client's mirror) → instant local
    preview while dragging.
  - `RandomEventManager` reads min/max interval from config in `scheduleNext()`
    (applies from the next scheduled fire).
- **Two caveats (documented in code):**
  - **Myopia blur radius is NOT in the menu** — it's a GLSL constant in
    `myopia.fsh`, not a runtime uniform, so it can't be nudged live yet (would need a
    uniform injection).
  - **`randomizerChestChance` is read live but loot tables only re-roll on resource
    load**, so that one knob takes effect on the next `/reload` or restart, not
    instantly (unlike audio/fog). All other knobs are truly live.
- **Editing tips:** to add a knob, extend the `ModConfig` record + `DEFAULT` + `CODEC`
  + `toArray`/`fromArray` + `ConfigManager` JSON + a `ConfigScreen` `Spec`, and bump
  the protocol. New lang keys `config.blind-deaf-muted.*` (en + fr) + keybind
  `key.blind-deaf-muted.open_config`.

### Recent additions (note card — muted's writing tool)

- **Note card item (`ModItems.NOTE_CARD`, idea for MUTED):** a square of paper you write
  on like a sign (≤6 lines, ≤22 chars/line) and brandish to show teammates, Sea-of-Thieves
  treasure-map style. Text lives in a custom data component (`ModComponents.CARD_TEXT`,
  `List<String>`, registered from `main` like items) — it carries BOTH a persistent codec
  and a **packet codec, so a held card's text auto-syncs to the clients tracking that
  player**; that's how everyone reads a brandished card with NO extra text packet (the
  feature renderer reads it off the tracked stack). Give via `/bdm card`.
- **Interaction (both on right-click, no keybind):** brandish = **right-click**
  (`UseItemCallback`, client-side, returns `ActionResult.SUCCESS`) → toggles, flips
  `CardBrandishState` locally + sends `CardBrandishPayload` C2S. Write = **sneak (shift) +
  right-click** → opens `CardEditScreen` (6 text fields on a paper panel, prefilled from the
  held card; on close sends `CardWritePayload` C2S → server writes the component
  authoritatively, clamping count+length). The old rebindable `G` write keybind
  (`key.blind-deaf-muted.write_card`) was removed (lang keys too; `hud…card_empty` hint now
  says sneak+right-click). `NoteCardController` also auto-clears the toggle when the card
  leaves the hand.
- **Sea-of-Thieves inversion (per the user):** *brandishing → others read it, you don't;
  not brandishing → only YOU read it.* Implemented as (reworked after first in-game test):
  - **Not brandishing** = card is just a normal vanilla held item (no 3D panel at all);
    the writer reads privately via `NoteCardHud`. **Brandishing** = vanilla item hidden,
    both arms raised, big 3D panel held up with text facing OUTWARD (-Z, the player's
    front) — viewers in front read it. Right-click truly toggles show/lower.
  - `NoteCardFeatureRenderer` (feature layer on all player renderers) draws the **3D paper
    card** attached to `body` (follows sneak tilt), ONLY while
    `CardBrandishState.isBrandishing(name)`. Text via `TextRenderer.draw` on the -Z face.
    **Unit gotcha (was the original bug):** feature-renderer matrices are in BLOCK units,
    ModelPart cuboids in px — all placement translates multiply by `PX = 1/16` (the first
    version translated whole blocks → card metres away + text 16× too big: "giant floating
    text, no paper"). Text needs no mirror compensation: the entity-render `scale(-1,-1,1)`
    flip makes glyph +x/+y project to a front viewer's screen right/down. Constants
    (`CARD_CENTER_DOWN=3.5px`, `CARD_FORWARD=9px`) may still want minor in-game polish.
    **Text auto-fits + fullbright** (2nd test-pass feedback "too dark, too small"): the
    font scale grows until the widest line fills the card width or the lines fill its
    height, capped at `MAX_TEXT_SCALE=0.02` (short notes render BIG; a worst-case
    22-char×6-line note shrinks back to ≈0.0045); lines are centred like a sign; paper +
    text render at `LightmapTextureManager.MAX_LIGHT_COORDINATE` (readable at night —
    it's a comms tool). Private-read HUD paper brightened; `CardEditScreen` world-dim
    lightened to `0x50` alpha.
  - `NoteCardHud` (drawn from `InGameHudMixin` TAIL) is the writer's **private 2D read**: a
    paper panel above the hotbar, shown only while holding a card AND not brandishing. Hides
    the instant you brandish.
  - `PlayerEntityModelMixin` raises both arms (pitch -0.9 so the hands land at the panel) and
    `ArmedEntityRenderStateMixin` hides the vanilla held-item model — **both gated on
    brandishing** now, not on merely holding the card.
  - **First-person "am I showing it?" feedback:** `HeldItemRendererMixin` (client mixin,
    registered in the client mixins json) — while the LOCAL player brandishes, both
    first-person hand passes are cancelled and replaced by a two-arm hold copied from
    vanilla's `renderMapInBothHands` (same sway/equip/pitch curves, `@Shadow renderArm`
    ×2) with the card panel sunk low (`BDM_CARD_SINK=0.6`, tunable) so just its TOP peeks
    into view — you see your raised arms + card top, but not the text (it faces the
    audience). Exact `renderFirstPersonItem`/`renderArm` signatures verified against the
    yarn-mapped jar via `javap`.
  - **Pre-made messages:** a column of 8 buttons LEFT of the book in `CardEditScreen`
    (Yes/No/Help!/Danger!/Follow me/Wait here/Come here/RUN!) — clicking REPLACES the whole
    note with the localized label (line 1, cursor at end; still editable before Done).
    Lang keys `card.blind-deaf-muted.preset.*` (en+fr). Write-screen background dim fully
    removed (any fill read as a black veil vanilla book-and-quill doesn't have).
- **Server:** `CardBrandishState` (like `MegaphoneState`) tracks who's brandishing; broadcast
  via `CardBrandishStatePayload` (S2C) on the roster tick + on each toggle. Cleared on
  disconnect. Protocol bumped to **v8**.
- **Textures:** `assets/blind-deaf-muted/textures/item/note_card.png` (16×16 inventory icon)
  + `textures/entity/note_card.png` (**32×32**, generated: ruled front face, plain darker
  back — the 12×16×1 cuboid needs a 26×18 UV area, which overflowed the 16×16 icon the
  renderer first pointed at → invisible paper). Item-model defs under `items/` +
  `models/item/`. Lang keys en+fr (`item…note_card`, `screen…card`,
  `hud…card_reading/card_empty`, `key…write_card`).

### Recent additions (muffle rebalance — client-validated, from PR `feat-muffle-effect`)

- **DEAF/MUTED voice + deaf ambient retuned to the values the client validated** in the
  `ToHold:feat-muffle-effect` PR (PR #1). That PR branched *before* the live-config refactor
  so it couldn't merge; its parameters were ported into main's `ModConfig`/`VoiceFx` instead of
  merging the branch (which would have reintroduced `static final` constants and regressed the
  slider menu).
- **`VoiceFx` algorithm now matches the validated build:**
  - DEAF (no megaphone) — **3-pole** cascaded low-pass "through a wall" (`lowpassStage` ×
    `DEAF_LOWPASS_POLES=3`) at `deafLowpassHz`, kept audible via `deafVolume` makeup gain
    (was a 2-pole `lowpass2`). `lowpass2` removed; muted now uses a single `lowpassCore` (1-pole).
  - DEAF + megaphone — near-transparent 1-pole (slot 0) + `saturate(deafMegaphoneVolume, 0.80)`
    (clean+loud; was `lowpass2`+`scale`). New `MEGAPHONE_CEILING=0.80` constant.
  - MUTED (no megaphone) — 1-pole box `lowpassCore(mutedLowpassHz)` + `scale(mutedVolume)`.
  - MUTED + megaphone — 1-pole `lowpassCore(mutedMegaphoneLowpassHz)` + `saturate(mutedMegaphoneVolume, 0.80)`.
- **New `ModConfig.DEFAULT` voice values (validated):** deaf `210 Hz / 1.1`, deaf+meg `3000 Hz / 1.1`,
  muted `300 Hz / 0.05`, muted+meg `1800 Hz / 1.1`. All still live-tunable via the `O` menu; the
  values map to `saturate` GAIN on the two megaphone paths (not `scale`). No protocol/field
  change — the 8 voice knobs are unchanged in shape.
- **`DeafMuffle` (client world/ambient) reverted to the PR's validated levels**
  (`LIGHT 0.20/1.0/40 … EXTREME 0.01/0.85/8`) — milder + longer range than the interim
  post-PR tuning that had crept onto main.
- **⚠ Defaults only apply to a FRESH config.** `ConfigManager` keeps every key already present in
  `config/blind-deaf-muted.json`, so a server with a saved config keeps its old numbers — hit
  *Reset defaults* in the `O` menu (or delete the json) to pick up the validated values.

### Recent additions (megaphone → timed burst + cooldown)

- **Megaphone is now a rate-limited burst, keyed to the PLAYER (UUID), not the item.**
  **Right-click** with the megaphone in hand (like any vanilla usable item) → a **5 s** active
  burst, then a **2 min** cooldown before it can fire again. Carrying several megaphones does
  NOT bypass the cooldown (it's per-player). Constants: `MegaphoneState.ACTIVE_MS` / `COOLDOWN_MS`.
  *(Was originally an `R` keybind + `MegaphonePayload` C2S — both removed; see below.)*
- **Server-authoritative** (`server/MegaphoneState` reworked from a UUID set into a timed state
  machine): per-UUID `activeUntil` + `readyAt` deadlines in concurrent maps, `System.currentTimeMillis()`
  (thread-safe read from the SVC audio threads + server thread, no shared clock). `tryActivate()`
  returns `ACTIVATED` / `ALREADY_ACTIVE` / `ON_COOLDOWN`.
- **Flow (right-click, no packet):** a server-side `UseItemCallback` in `BlindDeafMutedServer`
  (guards: `world.isClient` PASS, spectator PASS — the callback hooks in before vanilla's
  spectator check) catches a right-click with the megaphone and calls `activateMegaphone()`,
  which fires the burst or refuses and replies with an **action-bar** message (translated:
  `msg.blind-deaf-muted.megaphone_active` / `…_cooldown` with seconds left). The old client
  `MegaphoneController` (R keybind) + `MegaphonePayload` (C2S) were **deleted**, along with the
  `key.blind-deaf-muted.megaphone` lang keys — protocol bumped to **v12**. The vanilla item
  cooldown (below) also makes vanilla swallow right-clicks until usable again, so the
  ON_COOLDOWN action-bar branch is mostly a fallback.
- **Hotbar cooldown overlay (vanilla):** on activation the server sets a vanilla item cooldown
  (`player.getItemCooldownManager().set(stack, ticks)`) for the full burst+cooldown (~125 s), so
  the megaphone shows the white sweeping overlay in the hotbar, emptying exactly when usable
  again. Keyed by cooldown GROUP (= item id) so it covers every megaphone the player holds, and
  `ServerItemCooldownManager` auto-syncs it to the client. Since activation IS item use now,
  the overlay also blocks right-clicks until ready; the authoritative gate is still
  `MegaphoneState`.
- **Audio/visual now read the burst only.** `BlindDeafMutedVoicechatPlugin` megaphone check is
  just `megaphoneState.isActive(uuid)` (renamed `megaphoneActive`); the old "holding the item =
  continuous megaphone" path is gone (removed `holdsMegaphone`/`isMegaphone`/`serverPlayer`). The
  mouth/arm visual (`broadcastMegaphoneState`) shows only during the burst; it refreshes within
  ~1 s of the burst ending via the roster tick, while the audio stops exactly on time.
- Not persisted (transient); cleared on disconnect. **Burst length + cooldown are live `ModConfig`
  knobs** (`megaphoneBurstSeconds` default 5, `megaphoneCooldownSeconds` default 120) — two new
  sliders in the `O` menu (new `SECONDS` style; grid grew to 16 knobs / 8 rows × 2 cols). Server
  reads them at activation and passes them into `MegaphoneState.tryActivate(uuid, burstMs, cooldownMs)`;
  the same values drive the action-bar text + the hotbar cooldown-overlay length. Protocol bumped
  to **v9** (2 floats added to the `ConfigPayload` wire format). Same fresh-config caveat as the
  other tunables (an existing `blind-deaf-muted.json` keeps its values → reset in menu / delete json).

### Recent additions (Potion of Relief — co-op dragon-fight boost)

- **Throwable "Potion of Relief" (`ModItems.RELIEF_POTION`)** — a splash-style bottle
  (`ReliefPotionItem` + `ReliefPotionEntity`, modelled on the Randomizer). Craft: **water potion +
  diamond + lapis lazuli** (shapeless, `data/blind-deaf-muted/recipe/relief_potion.json`; the
  `minecraft:potion` ingredient accepts any potion — water is the cheap intended one, vanilla JSON
  can't match potion contents). On shatter, every player within range has their disability
  temporarily reduced. Green-sparkle + instant-effect particles, glass-break sound.
- **Relief is a REAL vanilla status effect** (`ModEffects.RELIEF`, registered from `main` like
  items) — so the player gets the standard potion interface for free: **top-right HUD icon +
  vanilla countdown**, inventory-screen entry, survives relog (saved on the player), milk clears
  it. Icon texture: `assets/blind-deaf-muted/textures/mob_effect/relief.png` (18×18, placeholder
  aqua-cross — repaintable; the vanilla `mob_effects` atlas scans every namespace's
  `textures/mob_effect/` dir automatically). Lang `effect.blind-deaf-muted.relief` en+fr. The old
  custom plumbing (`ReliefPayload` S2C + action-bar message + wall-clock ReliefManager) was
  REMOVED — protocol bumped **v11**.
- `ReliefPotionEntity.SHATTER_HANDLER` (installed in `BlindDeafMutedServer`) finds players within
  `reliefRangeBlocks` of the impact and applies a `StatusEffectInstance` (`reliefDurationSeconds`
  × 20 ticks, no particles, icon on). `server/ReliefManager` is now just a **thread-safe mirror**
  of who has the effect (immutable `Set<UUID>` rebuilt on the server thread every tick + on
  shatter), because the SVC audio threads can't touch entity state; `ReliefState` (client) reads
  the local player's effect directly (vanilla syncs it — no custom packet, `disabilityRemaining()`
  API unchanged so all the effect mixins are untouched).
- **All three disabilities scale by `reliefReductionPercent` (default 0.75 = −75%)** via a single
  scalar `remaining = 1 − reduction` (`client/ReliefState.disabilityRemaining()`, local player;
  server reads the config for voice):
  - **Voice** (`VoiceFx`, bound `ReliefManager`): DEAF listener + MUTED speaker lerp cutoff → clear
    (`RELIEF_CLEAR_HZ` 4 kHz) + volume → 1.0. The headline dragon-coordination win.
  - **Deaf world sound**: `DeafAudioFilter` low-pass gains lerp → 1.0 (clear); `SoundSystemMixin`
    hearing range + ambient volume lerp → normal.
  - **Blind fog** (`BackgroundRendererMixin`): fog end lerps out toward `RELIEF_FOG_END` (64).
  - **Blind blackout** (`InGameHudMixin`): black-fill alpha × remaining.
  - **Blind myopia**: a dedicated third pipeline `post_effect/myopia_relief.json` (Intensity=-1)
    gives **near-clear sight** (sharp to ~10 blocks, faint far smear, no vignette), overriding
    BOTH the cane step and no-cane harsh blur while relieved. The `Intensity` uniform now blends
    a relief/soft/hard param TRIPLE in `myopia.fsh` (`pick()`: -1→0 relief→soft, 0→1 soft→hard).
    Originally relief only stepped HARD→SOFT like a cane — invisible to a blind player already
    holding their cane (the bug that prompted this). Still stepped (pipeline uniforms are baked
    per-JSON), not scaled by `reliefReductionPercent`.
- **Relief downside (BLIND): nausea wobble, visual only.** While a BLIND player is relieved, the
  screen wobbles exactly like vanilla NAUSEA — NO `StatusEffectInstance` (no HUD icon, no inventory
  entry, nothing extra for milk to clear). `client/ReliefNauseaController` owns a ramped strength
  (in/out over ~1 s, peak `TARGET=1.0` = full nausea; lower to soften); `GameRendererNauseaMixin`
  injects it into `GameRenderer.renderWorld` (MixinExtras): `@WrapOperation` on the single
  `MathHelper.lerp` that computes the wobble intensity (lift via `max()`, so real nausea/portals
  still win, and the accessibility Distortion-Effects scale² still applies) + `@ModifyExpressionValue`
  forcing the `hasStatusEffect(NAUSEA)` branch true (nausea's gentle wobble speed, divisor 7).
  **Gotcha (why not write `ClientPlayerEntity.nauseaIntensity` directly):** vanilla treats
  intensity-without-the-effect as *standing in a nether portal* — `InGameHud` draws the purple
  portal overlay + `GameRenderer` uses the frantic portal wobble (divisor 20). First attempt did
  that and looked like a nether trip. Keeping our value out of the field = no overlay, true nausea
  look. Gated on `blindEffectActive()` + `ReliefState.localActive()`; purely client-side, no
  protocol change. Trade-off: clear sight during relief, but the world sways.
- **Config (3 new sliders, `O` menu):** `reliefReductionPercent` (PERCENT, 0.75), `reliefRangeBlocks`
  (BLOCKS, 8), `reliefDurationSeconds` (SECONDS, 30). Grid now 19 knobs / 10 rows × 2 cols. Protocol
  bumped **v10**. Fresh-config caveat applies (existing json keeps old values; new keys default in).
- Texture `textures/item/relief_potion.png` (generated aqua bottle, repaintable) + item/model defs +
  lang en/fr (`item…relief_potion`, `msg…relief_active`, `config…relief*`).

### Recent additions (relieved-muted gut noises)

- **Fart/burp on relieved MUTED talk (interval mode):** while a MUTED player under a
  Potion of Relief talks, a random gut noise fires every ~3.5 s (was 1–1.2 s — spammed) at
  their position for as long as they keep talking (a long phrase = steady comedy track),
  stopping within one interval of going quiet. Mechanic: any mic packet arms the next deadline via
  `putIfAbsent`; firing empties the slot; the next 20 ms mic packet re-arms it.
- **Voice duck under each noise:** for `DUCK_MS` (1 s) after each fire the speaker's voice
  drops to `RELIEF_NOISE_DUCK` (0.15×, constant in `VoiceFx`) so the noise interrupts the
  speech instead of layering under it (`MutedReliefNoise.isDucked` read per mic frame →
  `VoiceFx.distort(..., ducked)`, applied on both megaphone/no-megaphone paths). Window is
  fixed: the actual clip length is unknowable server-side — the weighted random file pick
  happens on EACH CLIENT (so two listeners may even hear different files; accepted quirk).
- **Files:** `common/ModSounds.java` (`muted_relief_noise` SoundEvent, registered from
  `main` like items/effects) + `server/MutedReliefNoise.java` (scheduler) + hooks in
  `BlindDeafMutedVoicechatPlugin.onMicrophone` (schedule, SVC audio thread) and a
  `END_SERVER_TICK` in `BlindDeafMutedServer` (fire — same thread split as `ReliefManager`).
  Role+relief re-checked at fire time (relief may expire / role re-roll during the delay).
  Cleared on disconnect. No protocol change (pure server-side + resource pack).
- **Sound pool:** 14 mono oggs in `assets/blind-deaf-muted/sounds/muted_relief/` +
  weighted random pick in `sounds.json` (weights 10 = short/common, 8 / 4, 2 = the long
  `rot-hulk`; user tunes weights by editing `sounds.json` only, no code). Files were
  converted stereo→mono (`ffmpeg -ac 1`) — REQUIRED for 3D positional playback; keep new
  oggs mono + lowercase-ASCII filenames. Random pitch 0.9–1.1 per play. Subtitle key
  `subtitles.blind-deaf-muted.muted_relief_noise` (en+fr). Constants in
  `MutedReliefNoise`: `SILENCE_GAP_MS`, `MIN/MAX_DELAY_MS`.

### Recent additions (card editor QoL + vanilla roster/recipes)

- **Note card auto line-wrap:** typing past a line's end in `CardEditScreen` word-wraps
  onto the next line (cascading, cursor follows; overflow past line 6 dropped).
- **Note card Ctrl+A:** selects the whole card (translucent blue highlight);
  backspace/delete wipes it, typing replaces it, any other key drops the selection.
- **Roster = vanilla scoreboard sidebar (replaces RosterHud):** server-owned
  `server/RosterScoreboard` maintains a `bdm_roster` objective in the SIDEBAR slot
  (right-middle, standard MC look), refreshed on the once/sec roster tick. Blank
  score numbers (`BlankNumberFormat`), per-score display text `Name  Role` (role
  colored + `Text.translatable` → per-client language; title key
  `scoreboard.blind-deaf-muted.roster_title`, en+fr). Anti-spoiler: updates skipped
  while `RoleManager.isRouletteRunning()` (~3.5 s after any `setAnimated`).
  `RosterHud` + `L` keybind DELETED; `RosterState` kept (accessory `roleOf()` +
  roulette freeze). Stale-line fix: cleanup reads the OBJECTIVE's own entries (scores
  persist in the world save; the old in-memory `shownNames` set missed pre-restart
  lines → offline players stayed listed until rejoin). Note: sidebar is drawn under the blind BLACKOUT fill, so a
  blacked-out blind player no longer sees the roster (old HUD drew over it).
- **Recipes in the recipe book — always unlocked:** all `blind-deaf-muted:*` recipes
  (cane, megaphone, note_card, randomizer, relief_potion) are unlocked server-side on
  JOIN (`player.unlockRecipes` filtered by namespace), so they're visible in the vanilla
  recipe-book UI from the first second — players see what to craft + which ingredients
  to hunt. (First pass used per-ingredient unlock advancements; removed as redundant.)

### Recent additions (piglin pearl boost)

- **Ender-pearl barter bonus:** vanilla piglin bartering's pearl entry is only ~2.2%
  (≈185 gold avg for 12 pearls) — too grindy. A bonus pool on
  `PIGLIN_BARTERING_GAMEPLAY` (next to the Relief pool in `BlindDeafMutedServer`)
  gives 2-4 pearls at 15% per barter (vanilla entry untouched → ~17% combined).
  Constants `PEARL_BARTER_BONUS_CHANCE` / `PEARL_BARTER_MIN/MAX`. Loot pools bake at
  resource load → change needs `/reload` or restart, like `randomizerChestChance`.
- **Relief ×3 from piglins:** piglin barter bonus + piglin death drop now give
  `RELIEF_DROP_COUNT` (3) Relief potions per hit (one per trio member); iron-golem /
  blaze death drops stay 1.
- **History-aware randomizer (anti-streak):** new `server/RoleHistory.java` — per-UUID
  counts of every role ever assigned, persisted to `config/blind-deaf-muted-roles.json`
  (survives restarts). Recorded from BOTH `RoleManager.set` + `setAnimated` (so manual
  `/bdm set` counts too; NONE ignored). `RoleRoller.rollAll` now runs a best-of-64
  trial search: each trial is a valid coverage deal, scored by summed history counts
  (+1000 penalty for landing on your current role); lowest score wins, then the old
  repair pass still guarantees no-repeat. Effect: role distribution evens out over a
  session ("often blind" fixed) while staying random (ties random, not a rotation).
  Delete the json to reset the memory. Constants: `DEAL_TRIALS`, `REPEAT_PENALTY`.
  **Never blocks** (user's hard requirement): no retry-until-success anywhere — fixed
  64 trials, best available deal always applied; history/no-repeat are soft scores,
  the roulette always completes.
  **Coverage is HARD (2nd user hard requirement):** with 3+ players every disability
  is ALWAYS distributed — structurally true (cyclic deal + swap-only repair for 3+),
  plus an explicit enforcement pass after repair that force-replaces a duplicated
  role with any missing one (so future edits can't silently break it). Priority
  order: never-block > full coverage > no-repeat > history balance.
- `blindArrowCrystal` stays OFF by default (0.0 in `ModConfig.DEFAULT` AND in
  `docker/data/config/blind-deaf-muted.json`); enable per-session via `O` menu.

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
- Random events — first pass done (`RandomEventManager`); add more event types
  (e.g. swap two players' positions, time/weather shifts, mob spawn) as desired.
- Command syntax & name finalization.

### Localization (i18n)

- **French supported.** Player-facing text uses translation keys resolved per-client
  (`Text.translatable`), so a client set to *Français* shows French even though the
  server runs the logic. Keys live in `assets/blind-deaf-muted/lang/en_us.json` +
  `fr_fr.json`. `Role.translationKey()` returns `role.blind-deaf-muted.<name>`; the
  role announce (`RoleManager`), roulette reveal (`RouletteAnimation`), roster HUD
  labels + roster/tracker toggles, item names and keybinds all go through keys.
- **Still hardcoded English (by choice):** admin `/bdm` command feedback (host/op
  console) and the `B`/`N` debug action-bars. Convert to keys if the host needs FR too.

## Reference

- "Completely Blind" mod (inspiration only, not reusable — client-only, no server
  control): <https://www.curseforge.com/minecraft/mc-mods/completely-blind>
