package com.blinddeafmuted.server;

import com.blinddeafmuted.common.Role;
import de.maxhenkel.voicechat.api.Entity;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.EntityAudioChannel;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The DEAF relief "ghost voice" scheduler: while a DEAF player is under a Potion
 * of Relief, this replays curated voice snippets from other players — clean and
 * undeformed — at random intervals, positioned on nearby players so the deaf
 * player can't tell if what they hear is live or a replay.
 *
 * <p>This replaces the old tinnitus (acouphene) as the deaf relief's downside:
 * instead of an annoying ring, the player gets <em>confusion</em> — voices
 * they heard earlier replayed as if someone just said it.
 *
 * <p>Works via the SVC {@link EntityAudioChannel} + {@link AudioPlayer} API:
 * the server creates a temporary audio channel attached to a nearby player
 * entity, feeds it the PCM audio of a snippet, and the AudioPlayer handles
 * the 20 ms frame pacing automatically. A {@code setFilter} ensures only the
 * target deaf player hears it.
 *
 * <p><b>Threading.</b> {@link #tick} runs on the server tick. SVC's
 * AudioPlayer runs its own audio thread; we only create/start it here.
 */
public final class DeafReliefVoices {

    private static final Logger LOGGER = LoggerFactory.getLogger("blind-deaf-muted-ghost-voices");

    // ---- Tunables ----------------------------------------------------------
    // Cutoffs and intervals are now read live from ModConfig.

    // ---- State -------------------------------------------------------------

    /** Next scheduled replay time per deaf+relieved player. */
    private final ConcurrentHashMap<UUID, Long> nextPlayAt = new ConcurrentHashMap<>();

    private final RoleManager roles;
    private final ReliefManager relief;
    private final VoiceSnippetCache cache;
    private final java.util.function.Supplier<com.blinddeafmuted.common.ModConfig> config;

    /** Set once from the SVC plugin's initialize(). */
    private volatile VoicechatServerApi serverApi;

    public DeafReliefVoices(RoleManager roles, ReliefManager relief, VoiceSnippetCache cache,
                            java.util.function.Supplier<com.blinddeafmuted.common.ModConfig> config) {
        this.roles = roles;
        this.relief = relief;
        this.cache = cache;
        this.config = config;
    }

    /** Provide the SVC server API. Called once from the voicechat plugin init. */
    public void bindServerApi(VoicechatServerApi api) {
        this.serverApi = api;
    }

    /**
     * Server tick handler. For each DEAF + relieved player, check if it's time
     * to play a ghost snippet.
     */
    public void tick(MinecraftServer server) {
        VoicechatServerApi api = this.serverApi;
        if (api == null) return; // SVC not loaded

        long now = System.currentTimeMillis();

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID playerId = player.getUuid();

            boolean active = roles.get(player) == Role.DEAF && relief.isActive(playerId);
            if (!active) {
                nextPlayAt.remove(playerId);
                continue;
            }

            // Schedule the first replay if we haven't yet.
            Long scheduledAt = nextPlayAt.get(playerId);
            if (scheduledAt == null) {
                // First tick as deaf+relieved: schedule after a short initial delay.
                nextPlayAt.put(playerId, now + randomInterval());
                continue;
            }

            if (now < scheduledAt) continue; // not yet time

            // Time to play! Remove the schedule (re-armed after playback).
            nextPlayAt.remove(playerId);

            // Check if we have material.
            if (!cache.hasMaterial(playerId)) {
                // No snippets yet — re-schedule and let the client-side tinnitus
                // continue as fallback.
                nextPlayAt.put(playerId, now + randomInterval());
                continue;
            }

            VoiceSnippetCache.ScoredSegment snippet = cache.getSnippet(playerId);
            if (snippet == null) {
                nextPlayAt.put(playerId, now + randomInterval());
                continue;
            }

            // Find a nearby player to attach the voice to (for positional confusion).
            ServerPlayerEntity anchor = pickNearbyPlayer(server, player, snippet.speaker);

            if (anchor == null) {
                // No one nearby — skip this round, re-schedule.
                nextPlayAt.put(playerId, now + randomInterval());
                continue;
            }

            // Play the snippet via SVC AudioChannel + AudioPlayer.
            playSnippet(api, player, anchor, snippet);

            // Re-schedule next snippet.
            nextPlayAt.put(playerId, now + randomInterval());
        }
    }

    /** Drop a leaver's tracking. */
    public void clear(UUID player) {
        nextPlayAt.remove(player);
    }

    // ---- Internals ---------------------------------------------------------

    /**
     * Pick a nearby player to attach the ghost voice to. Prefers the original
     * speaker if they're nearby (their voice coming from their position is the
     * most confusing), otherwise picks a random nearby player.
     */
    private ServerPlayerEntity pickNearbyPlayer(MinecraftServer server, ServerPlayerEntity deaf,
                                                 UUID originalSpeaker) {
        List<ServerPlayerEntity> nearby = new ArrayList<>();
        ServerPlayerEntity originalNearby = null;

        for (ServerPlayerEntity candidate : server.getPlayerManager().getPlayerList()) {
            if (candidate == deaf) continue;
            if (!candidate.getWorld().equals(deaf.getWorld())) continue;
            double range = config.get().deafReliefVoicesNearbyRangeBlocks();
            if (candidate.squaredDistanceTo(deaf) > range * range) continue;

            nearby.add(candidate);
            if (candidate.getUuid().equals(originalSpeaker)) {
                originalNearby = candidate;
            }
        }

        // Prefer the original speaker if nearby.
        if (originalNearby != null) return originalNearby;

        // Otherwise pick a random nearby player.
        if (nearby.isEmpty()) return null;
        return nearby.get(ThreadLocalRandom.current().nextInt(nearby.size()));
    }

    /**
     * Create an SVC EntityAudioChannel attached to {@code anchor}, filtered to
     * only the {@code deaf} player, and play the snippet's PCM through it.
     */
    private void playSnippet(VoicechatServerApi api, ServerPlayerEntity deaf,
                              ServerPlayerEntity anchor,
                              VoiceSnippetCache.ScoredSegment snippet) {
        try {
            // Wrap the MC entity as an SVC Entity.
            Entity svcEntity = api.fromEntity(anchor);
            if (svcEntity == null) return;

            // Create a unique channel for this playback.
            UUID channelId = UUID.randomUUID();
            EntityAudioChannel channel = api.createEntityAudioChannel(channelId, svcEntity);
            if (channel == null) {
                LOGGER.warn("[ghost-voices] Failed to create audio channel for {}",
                        anchor.getName().getString());
                return;
            }

            // Filter: only the deaf player hears this channel.
            UUID deafId = deaf.getUuid();
            channel.setFilter(sp -> sp.getUuid().equals(deafId));

            // Create encoder + audio player. SVC handles 20ms frame pacing.
            OpusEncoder encoder = api.createEncoder();
            if (encoder == null) {
                LOGGER.warn("[ghost-voices] Failed to create Opus encoder");
                return;
            }

            AudioPlayer audioPlayer = api.createAudioPlayer(channel, encoder, snippet.pcm);
            audioPlayer.setOnStopped(() -> {
                // Clean up the encoder when playback finishes.
                encoder.close();
                LOGGER.debug("[ghost-voices] Snippet playback finished for deaf={}, anchor={}, {}ms",
                        deaf.getName().getString(), anchor.getName().getString(), snippet.durationMs);
            });
            audioPlayer.startPlaying();

            LOGGER.debug("[ghost-voices] Playing snippet for deaf={}, anchor={}, speaker={}, {}ms, score={:.2f}",
                    deaf.getName().getString(), anchor.getName().getString(),
                    snippet.speaker, snippet.durationMs, snippet.score);
        } catch (Exception e) {
            LOGGER.warn("[ghost-voices] Failed to play snippet", e);
        }
    }

    /** Random interval between ghost-voice replays. */
    private long randomInterval() {
        com.blinddeafmuted.common.ModConfig cfg = config.get();
        long minMs = (long) (cfg.deafReliefVoicesIntervalMinSeconds() * 1000);
        long maxMs = (long) (cfg.deafReliefVoicesIntervalMaxSeconds() * 1000);
        if (minMs > maxMs) minMs = maxMs; // fail-safe for weird slider states
        return ThreadLocalRandom.current().nextLong(minMs, maxMs + 1);
    }
}
