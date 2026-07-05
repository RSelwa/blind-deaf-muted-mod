package com.blinddeafmuted.server;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side record of which players are currently under a Potion of Relief (their
 * disability temporarily reduced), keyed by player UUID with a wall-clock expiry.
 *
 * <p>Read from two threads — the SVC audio threads ({@code BlindDeafMutedVoicechatPlugin}
 * via {@link #isActive}, so a relieved deaf listener / muted speaker hears/speaks clearer)
 * and the server thread (applying + the visual broadcast). {@link System#currentTimeMillis()}
 * deadlines in a concurrent map → lock-free reads, no shared clock. Not persisted; cleared on
 * disconnect. Same shape as {@link MegaphoneState}.
 */
public final class ReliefManager {

    /** Wall-clock ms at which each player's relief ends. */
    private final Map<UUID, Long> reliefUntil = new ConcurrentHashMap<>();

    /** Grant (or refresh) relief for this player, lasting {@code durationMs} from now. */
    public void apply(UUID player, long durationMs) {
        reliefUntil.put(player, System.currentTimeMillis() + durationMs);
    }

    /** Whether this player's relief is currently active. */
    public boolean isActive(UUID player) {
        if (player == null) return false;
        Long until = reliefUntil.get(player);
        return until != null && System.currentTimeMillis() < until;
    }

    /** Drop a player entirely (e.g. on disconnect). */
    public void clear(UUID player) {
        reliefUntil.remove(player);
    }
}
