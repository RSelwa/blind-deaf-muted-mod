package com.blinddeafmuted.server;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side record of which players are currently holding the megaphone key
 * (push-to-megaphone, default {@code R} on the client). Fed by the inbound
 * {@code MegaphonePayload}; read by {@link BlindDeafMutedVoicechatPlugin} (off the
 * Simple Voice Chat threads, hence the concurrent set) to decide whether a DEAF
 * listener should hear this speaker loud + saturated; and snapshotted each roster
 * tick into {@code MegaphoneStatePayload} so every client can draw the mouth model.
 *
 * <p>Not persisted — it's transient input state, cleared naturally when a player
 * releases the key or disconnects (see {@link #clear}).
 */
public final class MegaphoneState {

    /** UUIDs of players whose megaphone key is currently down. */
    private final Set<UUID> active = ConcurrentHashMap.newKeySet();

    /** Apply a press (active=true) or release (active=false) for one player. */
    public void set(UUID player, boolean on) {
        if (on) {
            active.add(player);
        } else {
            active.remove(player);
        }
    }

    /** Whether this player is currently megaphoning. */
    public boolean isActive(UUID player) {
        return player != null && active.contains(player);
    }

    /** Drop a player entirely (e.g. on disconnect) so a stuck key can't linger. */
    public void clear(UUID player) {
        active.remove(player);
    }
}
