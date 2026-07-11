package com.blinddeafmuted.server;

import com.blinddeafmuted.common.ModSounds;
import com.blinddeafmuted.common.Role;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The comedic price of relief for a MUTED player: while they talk (their voice passes
 * near-clear during a Potion of Relief), a random gut noise (fart/burp —
 * {@link ModSounds#MUTED_RELIEF_NOISE}, weighted pool in sounds.json) plays at their
 * position every ~1–1.2 s, for everyone nearby. A long phrase = a steady comedy track;
 * a short word = one noise ~1 s after it.
 *
 * <p><b>Interval mechanic.</b> Any mic packet schedules the next noise if none is
 * pending ({@code putIfAbsent}); firing clears the slot, and the very next mic packet
 * (SVC sends one every 20 ms while talking) re-arms it — so noises repeat every
 * delay-interval for as long as they keep talking, and stop within one delay of them
 * going quiet.
 *
 * <p><b>Ducking.</b> Each fire also opens a {@link #DUCK_MS} window during which the
 * speaker's voice is dropped low ({@link BlindDeafMutedVoicechatPlugin} reads
 * {@link #isDucked} per mic frame → {@code VoiceFx.distort} scales the volume), so the
 * noise reads as INTERRUPTING their speech, not layered under it. Fixed window: the
 * actual clip length is unknowable server-side (the weighted random file pick happens
 * on each client), so we approximate with the typical clip duration.
 *
 * <p><b>Threading.</b> "They're talking" is detected on the Simple Voice Chat audio
 * threads ({@link BlindDeafMutedVoicechatPlugin#onMicrophone} → {@link #onTalk}),
 * which must not touch entity/world state — so onTalk only records a deadline in a
 * concurrent map, and the server tick ({@link #tick}) fires the actual sound. Same
 * split as {@link ReliefManager}. {@code System.currentTimeMillis()} for the same
 * reason as {@link MegaphoneState}: no shared clock with the audio threads.
 * Role/relief checks run BOTH at schedule time (cheap early-out) and at fire time
 * (relief may have expired during the delay).
 */
public final class MutedReliefNoise {

    /** Gap between noises while the player keeps talking; also the delay from talk
     *  start to the first noise. (Was 1–1.2 s — spammed; raised to ~3.5 s in test.) */
    private static final long MIN_DELAY_MS = 3500;
    private static final long MAX_DELAY_MS = 3700;

    /** How long the speaker's voice stays ducked after each noise fires. Approximates
     *  the typical clip length (pool runs 0.15–2.4 s, most ≤1 s) — the true length is
     *  unknowable server-side (each client rolls the weighted pick itself). */
    private static final long DUCK_MS = 1000;

    /** Pending noise deadlines (audio threads schedule, server tick fires+removes). */
    private final ConcurrentHashMap<UUID, Long> dueAtMs = new ConcurrentHashMap<>();

    /** Voice-duck windows (server tick opens on fire, audio threads read). */
    private final ConcurrentHashMap<UUID, Long> duckUntilMs = new ConcurrentHashMap<>();

    private final RoleManager roles;
    private final ReliefManager relief;

    public MutedReliefNoise(RoleManager roles, ReliefManager relief) {
        this.roles = roles;
        this.relief = relief;
    }

    /** A MUTED speaker's mic packet arrived. Called from the SVC audio threads —
     *  entity/world state must NOT be touched here; only (re-)arm the next noise. */
    public void onTalk(UUID speaker) {
        if (!relief.isActive(speaker)) return;
        // putIfAbsent: while a noise is pending its deadline stays put; once it fires
        // the slot empties and the next mic packet re-arms it → interval repeat.
        dueAtMs.putIfAbsent(speaker, System.currentTimeMillis()
                + ThreadLocalRandom.current().nextLong(MIN_DELAY_MS, MAX_DELAY_MS + 1));
    }

    /** Whether this speaker's voice should currently be ducked under a playing noise.
     *  Safe from any thread (read per 20 ms mic frame). */
    public boolean isDucked(UUID speaker) {
        Long until = duckUntilMs.get(speaker);
        return until != null && System.currentTimeMillis() < until;
    }

    /** Fire any due noises. Server thread only (END_SERVER_TICK). */
    public void tick(MinecraftServer server) {
        if (dueAtMs.isEmpty()) return;
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> it = dueAtMs.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            if (entry.getValue() > now) continue;
            it.remove();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            // Re-check at fire time: relief can expire (or the role re-roll away)
            // during the 1s delay — no noise then.
            if (player == null || roles.get(player) != Role.MUTED
                    || !relief.isActive(player.getUuid())) {
                continue;
            }
            duckUntilMs.put(player.getUuid(), now + DUCK_MS);
            // Slight pitch wobble so back-to-back noises don't sound identical even
            // when the weighted pool repeats a file.
            float pitch = 0.9F + player.getWorld().getRandom().nextFloat() * 0.2F;
            player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                    ModSounds.MUTED_RELIEF_NOISE, SoundCategory.PLAYERS, 1.0F, pitch);
        }
    }

    /** Drop a leaver's tracking so the maps can't accumulate stale UUIDs. */
    public void clear(UUID player) {
        dueAtMs.remove(player);
        duckUntilMs.remove(player);
    }
}
