package com.blinddeafmuted.server;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side megaphone usage state: a timed burst with a cooldown, keyed by PLAYER UUID
 * (not by item), so a player can't dodge the cooldown by carrying several megaphones.
 *
 * <p>Pressing the megaphone key (while holding a megaphone item) fires a burst during which the
 * voice-chat plugin renders the speaker loud (cuts through deafness). When the burst ends the
 * player enters a cooldown lockout before they can fire again. Both durations are the live
 * {@code megaphoneBurstSeconds} / {@code megaphoneCooldownSeconds} knobs from {@code ModConfig}
 * (slider menu) — passed into {@link #tryActivate} by the caller. Total lockout from one
 * activation to the next = burst + cooldown.
 *
 * <p>Read from two places on different threads — the SVC audio threads
 * ({@link BlindDeafMutedVoicechatPlugin} via {@link #isActive}) and the server thread
 * (activation + the visual broadcast). All timing is wall-clock ({@link System#currentTimeMillis()})
 * held in concurrent maps, so no shared mutable clock and the reads are lock-free. Not
 * persisted — transient; cleared on disconnect (see {@link #clear}).
 */
public final class MegaphoneState {

    /** Outcome of an activation attempt. */
    public enum Result { ACTIVATED, ALREADY_ACTIVE, ON_COOLDOWN }

    /** Wall-clock ms at which each player's current burst ends. */
    private final Map<UUID, Long> activeUntil = new ConcurrentHashMap<>();
    /** Wall-clock ms at which each player may megaphone again (= burst end + cooldown). */
    private final Map<UUID, Long> readyAt = new ConcurrentHashMap<>();

    /**
     * Try to start a burst for this player, lasting {@code burstMs} then locked out for a further
     * {@code cooldownMs}. Succeeds only if they're not already mid-burst and not on cooldown.
     */
    public Result tryActivate(UUID player, long burstMs, long cooldownMs) {
        long now = System.currentTimeMillis();
        if (isActive(player)) return Result.ALREADY_ACTIVE;
        Long ready = readyAt.get(player);
        if (ready != null && now < ready) return Result.ON_COOLDOWN;
        activeUntil.put(player, now + burstMs);
        readyAt.put(player, now + burstMs + cooldownMs);
        return Result.ACTIVATED;
    }

    /** Whether this player's megaphone burst is currently live. */
    public boolean isActive(UUID player) {
        if (player == null) return false;
        Long until = activeUntil.get(player);
        return until != null && System.currentTimeMillis() < until;
    }

    /** Milliseconds left before this player can megaphone again (0 if ready now). */
    public long cooldownRemainingMs(UUID player) {
        Long ready = readyAt.get(player);
        if (ready == null) return 0L;
        return Math.max(0L, ready - System.currentTimeMillis());
    }

    /** Drop a player entirely (e.g. on disconnect) so nothing lingers. */
    public void clear(UUID player) {
        activeUntil.remove(player);
        readyAt.remove(player);
    }
}
