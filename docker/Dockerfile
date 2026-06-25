# Multi-stage build: compile the server jar, then overlay it onto a ready-made
# Fabric Minecraft server image. `docker compose up` from /docker is all the host runs.

# ── Stage 1: build the server jar from source ────────────────────────────────
FROM gradle:8-jdk21 AS build
WORKDIR /src
# Build context is the repo root (see docker-compose.yml), so copy the whole repo.
COPY . /src
# remapJar produces the single loader-ready server-<version>.jar (with :common
# nested via jar-in-jar) and skips the -sources jar, so the COPY glob below is
# unambiguous.
RUN gradle :server:remapJar --no-daemon

# ── Stage 2: the actual server image ─────────────────────────────────────────
FROM itzg/minecraft-server:latest
# itzg image handles vanilla download, Fabric install, EULA, and world data.
ENV EULA=TRUE \
    TYPE=FABRIC \
    VERSION=1.21.4
# Drop our mod into the mods folder the image scans on startup.
# The jar is named after the Gradle module (server-<version>.jar); the -sources
# jar is excluded by matching only the plain artifact.
COPY --from=build /src/server/build/libs/server-*.jar /mods/server.jar
