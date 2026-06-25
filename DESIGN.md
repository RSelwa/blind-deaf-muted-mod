# Minecraft Mod — "Monkeys" (working title)

A cooperative challenge mod where players are assigned **disabilities** (blind, deaf,
muted) and must communicate around their limitations to **beat the Ender Dragon**,
in hardcore or normal mode.

---

## 1. Core idea

- 3+ friends play together.
- Each player is assigned a disability role:
  - **Blind** — screen goes black, can't see.
  - **Deaf** — hears no audio.
  - **Muted** — can't communicate (no chat / no voice out).
- Their challenge: combine their partial abilities, relay information to each other,
  and cooperatively kill the Ender Dragon.
- Playable in **hardcore or normal**.
- Admin can **move players between roles** live via commands.
- **Random events** are planned for later (not designed yet).

Design goals: **easy to manage**, **runs on the latest Minecraft version**, and
**no conflict with shaderpacks or other client mods**.

---

## 2. Architecture (decided)

Two small components that talk to each other:

### A. Server component (Fabric server mod or Paper plugin)
Owns the game logic:
- Assigns and stores each player's role.
- Admin commands (e.g. `/disability set <player> <blind|deaf|mute>`, team moves).
- Hardcore toggle (vanilla setting).
- Later: random events.
- **Tells each client what its role is** via a small custom packet.

### B. Thin client mod (Fabric)
Receives its role from the server and applies the effect **locally**:
- **Blind** → black screen overlay, rendered client-side.
- **Deaf** → set all sound-category volumes to 0 (or cancel sound events locally).
- **Muted** → block chat send / voice input.
- Reacts **live** when the server changes the player's role.

**Link between them:** one tiny custom network packet (role → client).

**Loader: Fabric**, for both pieces — best latest-version support and cleanest
shader / Sodium / Iris compatibility. See §2b for the comparison that backs this.

### 2b. Mod loader comparison (2026) — why Fabric

| Loader | Position in 2026 | Best for |
|---|---|---|
| **Fabric** | Performance king, lightweight, **fastest to update to new MC versions**. Owns Sodium / Lithium / Iris (shaders). | Small/custom mods, QoL, performance, **shader compatibility** |
| **NeoForge** | The **new standard for content/tech modding** on 1.21+. ~93% of popular mods target it; nearly every new big modpack (ATM 10, Better MC 5) runs on it. | Heavy content modpacks, tech mods |
| **Forge** | Legacy. Only for older packs (1.12.2, 1.16.5, 1.19.2, many 1.20.1). Slower to update, heavier. | Old modpacks that never migrated |

**Decision: Fabric.** We are building a *small custom mod* that needs shader /
Sodium / Iris compatibility and fast updates to the latest version — not interop
with a giant content-mod ecosystem (the only reason to pick NeoForge). NeoForge's
2026 momentum is about modpacks and doesn't change our calculus. Forge is legacy.

### 2c. Server deployment: Docker

The server will run in **Docker**. Plan: a Fabric server container (e.g. based on
an image like `itzg/minecraft-server`, which supports `TYPE=FABRIC`), with the
server mod jar dropped into the mounted `mods/` volume. Benefits: reproducible
setup, easy version pinning, simple to hand to users, isolated from the host.
Clients still install the thin client jar locally (Docker is server-side only).

### 2d. Monorepo layout (Fabric multi-project Gradle)

```
minecraft-mod-monkeys/
├── common/              # shared: role enum, the network packet, version constant
├── client/              # client mod jar (blind/deaf/mute effects)
├── server/              # server mod jar (roles, commands, game logic)
├── docker/
│   ├── Dockerfile
│   └── docker-compose.yml
├── gradle.properties    # SINGLE source of truth: MC version + mod version
├── settings.gradle      # includes common, client, server
├── DESIGN.md
└── CLAUDE.md
```

`common` keeps client and server in lockstep — both depend on it, so the packet
format and version constant can never disagree.

### 2e. Dockerfile approach

Surcharge the standard MC server image by copying our built server jar into `/mods`.
Multi-stage so `docker build` is self-contained from source:

```dockerfile
FROM gradle:8-jdk21 AS build
COPY . /src
RUN gradle :server:build           # -> server/build/libs/monkeys-server.jar

FROM itzg/minecraft-server:latest
ENV EULA=TRUE TYPE=FABRIC VERSION=LATEST
COPY --from=build /src/server/build/libs/monkeys-server.jar /mods/
```

`docker compose up` → downloads vanilla + Fabric, drops in our mod, runs. One command.

### 2f. Version sync (the main friction risk)

Client and server MUST match. Three cheap defenses:

1. **Single source of truth** — one `gradle.properties` defines MC + mod version for
   both jars; they can't drift.
2. **Release as a pair** — every git tag publishes the server image + client jar
   together, same version number, same release page.
3. **Version handshake** — client sends its version on join; on mismatch the server
   shows a clear "download v.X here" message instead of a cryptic crash.

### 2g. Who does what / delivery

- **Host = the server.** Running the container IS the server — no separate setup.
  The image does everything internally (vanilla download, Fabric, mod install, EULA,
  world persistence). **Only the host runs anything, and it's one command.**
- **Players** install Fabric Loader + Fabric API (one-time) and drop the matching
  `monkeys-client.jar` in `mods/`. Minimal, not zero.
- **Networking** is the only non-automatic part (inherent to any MC server, not our
  mod): LAN = share local IP; over internet = port-forward 25565, use a tunnel like
  `playit.gg`, or run on a VPS.

**Lowest-friction delivery (recommended):** CI builds the jars and publishes a
prebuilt server image to GHCR, so the host's `docker-compose.yml` just references
`image: ghcr.io/you/monkeys-server:<version>` — no source, no Gradle, no build.

**Future "click-and-play" client option:** ship the client as a Modrinth modpack
(one-click install in Prism/Modrinth launcher, auto-pulls the right MC + Fabric +
our jar) to erase the last bit of client friction.

---

## 3. Why client-side suppression (key decision)

We moved the disability effects to the **client mod** instead of enforcing them
purely server-side. This is the better design:

- **Deaf becomes complete & clean.** A client mod can mute *all* audio, including
  client-generated sounds (footsteps, UI, music) that a server can never touch.
  No leaks, no ProtocolLib packet juggling.
- **No server performance hit.** No per-sound packet interception → avoids the
  "big server problem."
- **Blind stays easy** — local black overlay, shader-compatible.

**Cost:** each player installs **one shared client jar**. Accepted, because it buys
complete, clean enforcement (including deaf) with no server strain. The jar is
Fabric + shader-safe, so it coexists with Sodium / Iris / shaderpacks.

---

## 4. The unavoidable enforcement rule

A mod can only enforce what passes **through Minecraft**:

- **Blind** is *always* enforceable (client render effect), in every mode.
- **Deaf** and **Muted** are only truly enforceable if comms go **through the game**
  (proximity voice + chat). On **Discord**, the mod cannot stop a muted player from
  talking or a deaf player from listening.

### Comms-mode flexibility (decided: flexible, easily toggled)
Build a **comms-mode toggle** in config. Proximity voice (e.g. Simple Voice Chat)
is **optional / soft-dependency**; if absent, fall back to Discord/honor mode.

| Mode | Blind | Deaf | Muted | Client install |
|---|---|---|---|---|
| **In-game** (proximity voice + chat) | ✅ enforced | ✅ enforced | ✅ enforced | voice mod required |
| **Discord** (external voice) | ✅ enforced | ⚠️ honor-system + soft HUD cues | ⚠️ honor-system (in-game chat still blockable) | none |

In Discord mode the mod still helps without true enforcement: HUD reminder
("🔇 you are MUTED"), block in-game chat, trust players on voice.

---

## 4b. Role / communication loop (the core fun)

This is the heart of the mod: the three disabilities are chosen so that **no single
player owns all three I/O channels**, which *forces* them to relay information
through each other to do anything coordinated — above all, to kill the Ender Dragon.

### Channel matrix (what each role can / can't do)

There are three communication channels: **See** (visual input), **Hear** (audio
input), **Speak** (output — in-game voice + chat). Each role is missing exactly one:

| Role  | See | Hear | Speak | Enforced by (client) |
|-------|:---:|:----:|:-----:|----------------------|
| **Blind** | ❌ | ✅ | ✅ | `BlindOverlay` (opaque black HUD) |
| **Deaf**  | ✅ | ❌ | ✅ | `DeafHandler` (all sound volumes → 0) |
| **Muted** | ✅ | ✅ | ❌ | `MuteHandler` (blocks outgoing chat/voice) |

Read it the other way — each channel is owned by exactly **two** of the three roles:

- **See:** Deaf + Muted
- **Hear:** Blind + Muted
- **Speak:** Blind + Deaf

That overlap is the whole game. Any single fact has to *hop* between players to get
from where it's perceived to where it's acted on, because the perceiver and the
actor never share enough channels.

### The three archetypes the matrix creates

- **Deaf = the Scout / "eyes that broadcast."** Sees the world and can talk, but is
  deaf to every reply. Pure *outbound* — narrates what they see ("crystal on the
  north tower, dragon diving left!") and cannot receive corrections. Best kept at
  range, never in blind melee, because nobody can warn them.
- **Blind = the Coordinator / "switchboard in the dark."** The only role that both
  **hears** *and* **speaks**, but sees nothing. Receives the Deaf's narration plus
  all the dragon's audio cues (wingbeats, roar, crystal-beam hum, hostile growls —
  the Blind player leans hardest on sound), fuses it, and issues spoken orders. The
  hub everything routes through — and the one who can never personally verify a thing.
- **Muted = the Operator / "silent hands."** Full perception (sees **and** hears)
  but cannot say a word. The natural front-line fighter — only role with complete
  situational awareness — yet can't call for help or acknowledge an order out loud.
  Must communicate purely through **in-game actions** (see below).

### The forced loop, applied to the Ender Dragon fight

The dragon fight is the perfect stress test because it demands three things at once,
each gated behind a different channel:

1. **Find & destroy the End Crystals** (on top of the obsidian towers). They must be
   *seen* to be targeted → only **Deaf** or **Muted** can spot them. The Deaf can
   *also* shout their location; the Muted can only see them.
2. **Survive the dragon's dives & breath.** The telegraph is largely audio (the
   roar / wing-whoosh) → **Blind** and **Muted** hear it coming; the **Deaf** player
   gets *no* warning and must be told where to stand.
3. **Land hits on the dragon** (perch melee + ranged) → requires sight → **Deaf** or
   **Muted**, but the Deaf can't hear "behind you," so the **Muted** is the safest
   sustained damage dealer.

Putting it together, a single round of the fight has to flow around the triangle:

```
Deaf SEES a crystal / dragon position  ──speaks──▶  (only Blind & Muted can hear)
                                                          │
Blind HEARS it (+ the dragon's audio tells)  ──speaks──▶  consolidated order
                                                          │
Muted HEARS the order, SEES the target  ──acts──▶  destroys crystal / fires / dodges
                                                          │
Muted cannot speak  ──signals non-verbally──▶  Deaf (who can see) reads the signal
                                                          │
Deaf SEES the result  ──speaks──▶  closes the loop back to Blind
```

No shortcut exists: the Deaf can't hear whether anyone acted, the Blind can't see
whether the crystal is gone, and the Muted — who can confirm both — can't *say* so.
Each player is permanently dependent on the other two.

### The Muted's non-verbal channel (the crux to design well)

Because the Muted has the best awareness but no voice, the fun hinges on giving them
expressive **in-game** ways to signal that *don't* go through chat/voice:

- **Sneak toggling** as yes/no or a beat code (crouch = confirmed).
- **Block placement / breaking** in agreed patterns (a torch = "done"; an arrow built
  from blocks = "go this way").
- **Dropping specific items** as a code (ender pearl dropped = "crystal down").
- **Leading by movement** — physically walking toward the next objective / pulling a
  teammate by nudging.
- **Look-direction pointing** — the Deaf (who can see them) reads where the Muted is
  facing/aiming.

These are read by whichever teammate can **see** (Deaf or Muted) and then voiced by
whoever can **speak+hear** (Blind), which keeps the relay intact.

> ⚠️ **Loophole to rule on:** in-game **signs** are world edits, not chat, so
> `MuteHandler` won't block them — a Muted player could write paragraphs on a sign.
> Decide deliberately: either (a) embrace signs as the Muted's slow, costly "written
> note" channel (fits the fiction), or (b) also suppress sign text for Muted. Leaning
> toward **(a)** — it's effortful and physical, which keeps the tension. Recorded as
> an open decision in §7.

### Difficulty knobs (config, later)

- **Rotate roles mid-run** (the existing `/monkeys set` command) so no one settles
  into one job — forces re-learning the relay.
- **Comms mode** (§4) changes the loop's integrity: in-game voice = fully enforced;
  Discord = Deaf/Muted become honor-system, so the loop is softer.
- Optional **2-player degenerate check:** with only Blind+Deaf (no Muted) nobody can
  both see *and* hear, so the loop is even harsher — note this when balancing for 3+.

---

## 5. Reference: "Completely Blind" mod

<https://www.curseforge.com/minecraft/mc-mods/completely-blind>

- ✅ Proves client-side total blackout works and is shader-compatible.
- ❌ **Client-only, all-or-nothing** — blacks you out from the start, **no server
  control**, no commands, no role switching. Not directly usable.
- ❌ Forge / NeoForge only, up to 1.21.4.

Use as **inspiration**, not a building block. We build our own role-driven version.

---

## 6. Difficulty & feasibility verdict

Overall: **Medium difficulty**, **high feasibility**. Everything is **fully
enforceable** (deaf included) with no server strain.

- 🟢 **Easy:** client black overlay, client audio mute, client chat block, role
  assignment commands, hardcore toggle. (~70% of the mod.)
- 🟡 **Moderate:** server↔client role-sync packet, reacting to live role changes.
- 🟢 **Later / easy:** random events (bolt on once the role system exists).

Nothing here is blocked or impossible.

---

## 7. Open / next decisions

- [ ] Final mod name (current working title: "Monkeys").
- [x] **Role/communication loop design** — who can do what, and how the three roles
  are *forced* to relay information to beat the dragon (the heart of the fun). See §4b.
- [ ] **Muted + signs** — embrace in-game signs as the Muted's slow "written note"
  channel (leaning yes), or suppress sign text too? See §4b.
- [ ] Random events design.
- [x] **Loader: Fabric** (both pieces) — decided, backed by 2026 comparison (§2b).
- [x] **Server runs in Docker** (Fabric server container) — decided (§2c).
- [ ] Command syntax & permissions.
- [ ] Whether to bundle/recommend Simple Voice Chat for the in-game comms mode.

---

## 8. Constraints recap (from the user)

- Latest Minecraft version if possible.
- No conflict with shaderpacks or other client mods.
- Easy to manage (commands to move players between roles).
- Flexibility on comms (proximity chat optional, Discord fallback).
- 3+ players; hardcore or normal.
