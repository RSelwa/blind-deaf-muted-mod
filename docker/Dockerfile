# Multi-stage build: compile the mod jar, then overlay it onto a ready-made
# Fabric Minecraft server image. `docker compose up` from /docker is all the host runs.

# ── Stage 1: build the mod jar from source ───────────────────────────────────
FROM gradle:8-jdk21 AS build
WORKDIR /src
# Build context is the repo root (see docker-compose.yml), so copy the whole repo.
COPY . /src
# remapJar produces the single loader-ready blind-deaf-muted-<version>.jar (with
# :common nested via jar-in-jar) and skips the -sources jar, so the COPY glob below
# is unambiguous. This is the SAME unified jar players install; on a dedicated
# server Fabric runs the main/voicechat entrypoints and skips the client ones.
RUN gradle :mod:remapJar --no-daemon

# ── Stage 2: the actual server image ─────────────────────────────────────────
FROM itzg/minecraft-server:latest
# itzg image handles vanilla download, Fabric install, EULA, and world data.
ENV EULA=TRUE \
    TYPE=FABRIC \
    VERSION=1.21.4
# Drop our mod into the mods folder the image scans on startup.
# remapJar only emits the plain artifact (blind-deaf-muted-<version>.jar), so the
# glob is unambiguous; no -sources jar is produced by this task.
COPY --from=build /src/mod/build/libs/blind-deaf-muted-*.jar /mods/blind-deaf-muted.jar
