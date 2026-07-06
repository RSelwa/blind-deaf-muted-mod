package com.blinddeafmuted.server;

import com.blinddeafmuted.common.ModEffects;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Thread-safe mirror of which players currently have the Relief status effect (their
 * disability temporarily reduced by a Potion of Relief).
 *
 * <p>The vanilla {@code ModEffects.RELIEF} status effect on the player is the SOURCE OF
 * TRUTH (it carries the HUD icon + countdown, persists across relog, is cleared by milk).
 * But the SVC audio threads ({@code BlindDeafMutedVoicechatPlugin} via {@link #isActive})
 * must not touch entity state off the server thread — so the server thread rebuilds this
 * immutable snapshot once per tick ({@link #refresh}) and the audio threads read it
 * lock-free. Worst-case staleness is one tick (50 ms), inaudible.
 */
public final class ReliefManager {

    /** Immutable snapshot of who has the Relief effect; swapped whole each tick. */
    private volatile Set<UUID> relieved = Set.of();

    /** Rebuild the snapshot from the online players' actual effects. Server thread only. */
    public void refresh(Collection<ServerPlayerEntity> players) {
        Set<UUID> next = new HashSet<>();
        for (ServerPlayerEntity player : players) {
            if (player.hasStatusEffect(ModEffects.RELIEF)) {
                next.add(player.getUuid());
            }
        }
        relieved = Set.copyOf(next);
    }

    /** Whether this player's relief is currently active. Safe from any thread. */
    public boolean isActive(UUID player) {
        return player != null && relieved.contains(player);
    }
}
