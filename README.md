# Blind Deaf Muted — a co-op Minecraft disability challenge

> 3+ friends are each given a **disability** — **blind**, **deaf**, or **muted** —
> and have to communicate around their limits to **beat the Ender Dragon** (hardcore
> or normal). An admin can move players between roles live with a command.

This is a [Fabric](https://fabricmc.net/) mod for Minecraft **1.21.4**. For the full
design rationale see [`DESIGN.md`](DESIGN.md); for the project status and decisions
see [`CLAUDE.md`](CLAUDE.md).

---

## How it works (30-second version)

The mod ships as **one unified jar** (`blind-deaf-muted-<version>.jar`). The same
file is installed by every player AND run by the server — there is no separate
client/server download. Fabric runs the right code on each side automatically:

| Entrypoint  | Runs on              | Job                                                                                                                |
| ----------- | -------------------- | ----------------------------------------------------------------------------------------------------------------- |
| `main`      | server **and** client | Registers roles, the admin command, the shared item/entity + network packets. Stores who is what; syncs each client. |
| `client`    | each player's client | Receives the role packet and applies the effect locally — black screen / fog (blind), silenced audio (deaf), blocked chat + voice (muted). |
| `voicechat` | server (if SVC present) | Enforces deaf/muted on real in-game voice via the Simple Voice Chat plugin API. |

Why one jar: Modrinth's client/server environment flag is **per-project, not
per-file**, so two jars can't be tagged correctly. One jar with three entrypoints
sidesteps that — see [`CLAUDE.md`](CLAUDE.md). A `common` module (shared role list +
packets) is bundled **inside** the jar via jar-in-jar, so the one file is
self-contained.

Effects are **client-side on purpose**: only the player's own game can mute _all_
their audio or paint _their_ screen — and it keeps the mod shader-compatible. The
exception is in-game **voice**, which is enforced server-side through Simple Voice
Chat. See [`DESIGN.md` §3–4](DESIGN.md).

The **fun** is structural: each role is missing exactly one of _see / hear / speak_,
so no one player can perceive, report, and act alone — they're forced to relay
information through each other. Full breakdown in [`DESIGN.md` §4b](DESIGN.md).

---

## What each person needs

- **The host** (one person): runs the server. Easiest path is **Docker** — one
  command, no Java or Minecraft install required (see deployment sections below). The
  server auto-downloads Fabric API + Simple Voice Chat.
- **Every player** (including the host, if they also play): installs **Fabric Loader**,
  **Fabric API**, **Simple Voice Chat**, then drops the `blind-deaf-muted-<version>.jar`
  into their `mods/` folder. One-time setup. See [Player setup](#player-setup-everyone).

The mod version players install **must match the server's** — they're built and
released together. A mismatched client gets a "please update" warning, not a crash.

---

## Prerequisites

**To run the server (host):**

- [Docker](https://docs.docker.com/get-docker/) + Docker Compose (recommended), **or**
- a manual Fabric server + JDK 21 if you prefer not to use Docker (not covered here).

**To build the jar from source (only if you're modifying the mod):**

- **JDK 21** (the build targets Java 21 — Minecraft 1.21.x requires it).
- That's it — the repo ships a Gradle wrapper (`./gradlew`), so you don't need Gradle
  installed. Docker also builds the jar itself, so for a plain server run you don't
  need Java at all.

---

## Quick start (build the jar)

> Skip this if you're using Docker for the server and only need the **client** jar —
> grab a prebuilt jar from the project's releases instead.

```bash
# from the repo root
./gradlew :mod:build
```

Output:

- `mod/build/libs/blind-deaf-muted-0.1.0.jar` → install on the **server** AND in
  **each player's** `mods/` (it's the same file). `common` is bundled inside it via
  jar-in-jar; you don't ship `common` separately.

> **JDK 21 note:** the build needs a Java 21 toolchain. `gradle.properties` contains
> an `org.gradle.java.installations.paths` line listing JDK 21 locations.
> **Edit it to your own JDK 21** if Gradle can't auto-detect yours. On Windows set
> `JAVA_HOME` to your Adoptium JDK 21 and run `.\gradlew.bat :mod:build --no-daemon`.
> The Docker build is unaffected — it uses its own JDK 21 image.

---

## Player setup (everyone)

Each player does this once:

1. Install the **Fabric Loader** for Minecraft **1.21.4** — use the
   [official installer](https://fabricmc.net/use/installer/) or a launcher like
   [Prism](https://prismlauncher.org/) that does it for you.
2. Download **[Fabric API](https://modrinth.com/mod/fabric-api)** and
   **[Simple Voice Chat](https://modrinth.com/mod/simple-voice-chat)** for 1.21.4.
3. Put **all** of these into your Minecraft `mods/` folder:
   - `fabric-api-*.jar`
   - `voicechat-fabric-*.jar`
   - `blind-deaf-muted-*.jar` (must match the server's version)
4. Launch Minecraft with the Fabric profile, then connect to the host's address
   (see the deployment section the host used).

`mods/` lives in `.minecraft/mods/` (or your launcher's instance folder).

> Simple Voice Chat is an optional soft dependency: the mod still loads without it
> (in-game voice just isn't enforced), but the intended experience installs it.

---

## Deploying the server

Pick the one that matches your situation. Both assume Docker on the host.

### Option A — VPS (play over the internet with friends anywhere)

Best when players aren't on the same network. You rent a small Linux server.

1. **Get a VPS** (any provider — Hetzner, DigitalOcean, OVH, etc.). A 2 GB RAM box
   is enough for a few friends; 4 GB is comfortable. Pick one geographically near
   the group for lower ping.

2. **Install Docker** on it:

   ```bash
   curl -fsSL https://get.docker.com | sh
   ```

3. **Get the repo onto the VPS** and build + start the server:

   ```bash
   git clone <your-repo-url> blind-deaf-muted && cd blind-deaf-muted/docker
   docker compose up -d --build
   ```

   The image downloads vanilla 1.21.4, installs Fabric, drops in our mod,
   auto-downloads Fabric API + Simple Voice Chat, and runs. First boot takes a minute
   or two. Check it with `docker compose logs -f`.

4. **Open the firewall** for the Minecraft port (TCP **25565**) **and** the Simple
   Voice Chat port (UDP **24454**):

   ```bash
   # ufw (Ubuntu/Debian)
   sudo ufw allow 25565/tcp
   sudo ufw allow 24454/udp
   ```

   Also open them in your provider's web control panel if they have a separate
   firewall/security-group. **Without UDP 24454, players connect but voice silently
   fails to start.**

5. **Find the VPS's public IP** (your provider shows it; or `curl ifconfig.me` on the
   box). Players connect to **`<VPS_PUBLIC_IP>:25565`** (the `:25565` is optional —
   it's the default port). Optionally point a domain at the IP for a nicer address.

6. Players follow [Player setup](#player-setup-everyone) and join.

**Stop / update:** `docker compose down` to stop; `git pull && docker compose up -d --build`
to update after a code change. The world persists in `docker/data/` across restarts.

> **Security:** a VPS is exposed to the internet. Keep the OS updated, consider a
> whitelist (`WHITELIST` env var, below), and don't run other untrusted services on
> the same box. Minecraft has no built-in encryption of the connection beyond its
> own auth — a VPS or a tunnel is still safer than port-forwarding your home router.

### Option B — LAN (everyone on the same network — same house, same Wi-Fi)

Best for a couch/Wi-Fi session. No internet exposure, no port-forwarding.

1. On the **host machine** (a PC on the LAN with Docker installed):

   ```bash
   git clone <your-repo-url> blind-deaf-muted && cd blind-deaf-muted/docker
   docker compose up -d --build
   ```

2. **Find the host's local IP** (the `192.168.x.x` / `10.x.x.x` address):
   - **macOS:** `ipconfig getifaddr en0`
   - **Linux:** `hostname -I` (take the first address)
   - **Windows:** `ipconfig` → "IPv4 Address" under your active adapter

3. **Allow the ports through the host's local firewall** if it has one (Windows
   Defender Firewall will usually prompt the first time; allow TCP 25565 and UDP
   24454). Docker already publishes both to the host via the compose `ports:` mapping.

4. Players on the **same network** follow [Player setup](#player-setup-everyone) and
   connect to **`<HOST_LOCAL_IP>:25565`**.

That's it — nothing leaves your network.

> **Want LAN-style ease but players are remote?** Use a tunnel instead of a VPS:
> run the server locally (as above) and expose it with [`playit.gg`](https://playit.gg/)
> or [Tailscale](https://tailscale.com/) (a private mesh VPN — everyone joins the
> same virtual LAN, then connects to the host's Tailscale IP). No port-forwarding,
> no rented server. Remember the voice chat also needs UDP 24454 tunnelled.

---

## Using it in-game (admin commands)

The host (or any opped player) assigns roles. The command requires **op /
permission level 2**.

```
/bdm set <player> <blind|deaf|muted|none>   # assign / clear a role (instant)
/bdm random                                 # roulette-reveal a random role to everyone
/bdm randomizer                             # give yourself 4 Randomizer bottles (test)
/bdm health <on|off>                        # shared-health mode
/bdm skin <on|off>                          # show/hide the role accessories
/bdm events <on|off>                        # periodic random-events timer (re-roll / random potion)
/bdm events now                             # force-fire one random event now (testing/recording)
```

The **random-events timer** (off by default) fires every 3–8 minutes while on: it
either re-rolls everyone's role (roulette, like a Randomizer bottle) or drops a random
potion effect on a random player for 20s. `/bdm events now` triggers one on demand —
handy when recording. Tune intervals/effects in `server/RandomEventManager.java`.

Examples:

```
/bdm set Alice blind
/bdm set Bob deaf
/bdm set Carol muted
/bdm set Alice none     # clear a role
```

`/bdm set` applies **instantly** on that player's client. Roles currently reset on
server restart (persistence is a planned TODO — see `CLAUDE.md`).

A throwable **Randomizer bottle** re-rolls everyone's role when it shatters; it's
found in structure chests (dungeon, mineshaft, village weaponsmith, stronghold,
jungle/desert temple, nether bridge, bastion treasure) and from **Piglin bartering**.

To **op yourself**, run in the server console (`docker compose logs` won't accept
input — attach instead):

```bash
docker attach blind-deaf-muted-server      # then type:  op <yourname>     (Ctrl-P Ctrl-Q to detach)
```

**Hardcore mode** is the vanilla setting — enable it via the `HARDCORE` env var
(below) before the world is first created.

---

## Personalizing it

### Server world settings (no rebuild — just env vars)

The server image is [`itzg/minecraft-server`](https://docker-minecraft-server.readthedocs.io/),
so you can tune the world by adding environment variables in
[`docker/docker-compose.yml`](docker/docker-compose.yml) under `environment:`. Useful ones:

```yaml
environment:
  EULA: "TRUE"
  DIFFICULTY: "hard" # peaceful | easy | normal | hard
  HARDCORE: "true" # the mod supports hardcore — set before first launch
  MOTD: "Blind Deaf Muted challenge!" # server list description
  MAX_PLAYERS: "8"
  WHITELIST: "Alice,Bob,Carol" # lock the server to your group
  OPS: "Alice" # auto-op the admin who assigns roles
  MEMORY: "3G" # JVM heap; bump for more players
```

Full list: the itzg image docs. Change them, then `docker compose up -d` to apply.

### Change the Minecraft version

Versions are centralized in [`gradle.properties`](gradle.properties) — the single
source of truth. To retarget another MC version, update there **and** in two places:

1. `gradle.properties` — `minecraft_version`, `yarn_mappings`, `loader_version`,
   `fabric_version` (get the exact strings from <https://fabricmc.net/develop>).
2. `docker/Dockerfile` — the `VERSION=1.21.4` line.
3. `mod/src/main/resources/fabric.mod.json` — the `minecraft` dependency range.

Then rebuild. Note: Minecraft's internal APIs change between versions, so the
effect code may need small fixes — see the "Must-verify before first build" notes in
`CLAUDE.md`.

### Tweak the disability effects

The gameplay code lives in `mod/` (packages keep the logical split:
`com.blinddeafmuted.client.*` and `com.blinddeafmuted.server.*`):

| File                                                | Controls                | Easy tweaks                                                            |
| --------------------------------------------------- | ----------------------- | --------------------------------------------------------------------- |
| `client/mixin/InGameHudMixin.java` + `BlindHandler` | the blind black screen / fog | Switch blind mode (key `B`); tweak the fog distance.            |
| `client/DeafHandler.java`                           | muting audio            | Silence only some sound categories (e.g. keep music).                 |
| `client/MuteHandler.java`                           | blocking chat           | Add an on-screen "you are MUTED" toast.                               |
| `client/RoleHeadAccessoryFeatureRenderer.java`      | glasses/bandage/headset | Move/restyle the 3D role accessories.                                 |
| `server/BlindDeafMutedVoicechatPlugin.java`         | in-game voice enforcement | Adjust how deaf/muted cancel voice packets.                         |

Roles themselves live in `common/src/main/java/com/blinddeafmuted/common/Role.java` —
add a new role here and it automatically appears in the command's tab-completion.
You'd then add a matching handler on the client.

### Rename the command

The command literal `bdm` is defined in
`mod/src/main/java/com/blinddeafmuted/server/BlindDeafMutedCommand.java`. Change the
`literal("bdm")` call to rename it.

### Network port

Change the left side of the `ports:` mapping in `docker-compose.yml`
(`"25565:25565"` → e.g. `"25570:25565"`) to expose the server on a different host
port; players then use that port. Keep the **right** side `25565` (the in-container
port) unchanged. The voice port `24454/udp` works the same way.

---

## Troubleshooting

- **Client won't connect / "outdated" or protocol warning** — the mod version on the
  client and server don't match. Rebuild/redistribute as a pair.
- **Player has Fabric but the mod does nothing** — Fabric API (`fabric-api-*.jar`)
  is missing from their `mods/`, or they launched the vanilla profile instead of the
  Fabric one.
- **No voice / crossed-out mic** — Simple Voice Chat isn't installed on that client,
  the server is missing it, the client/server SVC versions don't match, or UDP 24454
  isn't open/reachable.
- **`docker compose up` fails to build** — make sure you ran it from the `docker/`
  folder (the compose file sets the build context to the repo root).
- **Effects don't apply after `/bdm set`** — confirm the target player has the mod
  jar installed; the server can store a role for anyone but only a modded client
  renders the effect.
- **Roles vanished after a restart** — expected for now; role persistence is a
  planned feature.

---

## Project layout

```
common/   shared Role enum + network packets + version  (bundled into mod via jar-in-jar)
mod/      the single shippable jar — client effects AND server logic
docker/   Dockerfile + docker-compose.yml (one-command server)
DESIGN.md the full design doc and rationale
CLAUDE.md project status, decisions, and build notes
```
