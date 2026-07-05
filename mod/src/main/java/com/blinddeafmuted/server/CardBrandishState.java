package com.blinddeafmuted.server;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side record of which players are currently brandishing their note card outward
 * (right-click toggle on the client). Fed by the inbound {@code CardBrandishPayload};
 * snapshotted each roster tick into {@code CardBrandishStatePayload} so every client can
 * flip the card FACE toward viewers.
 *
 * <p>Not persisted — transient input state, cleared when a player toggles off or
 * disconnects (see {@link #clear}). Same shape as {@link MegaphoneState}.
 */
public final class CardBrandishState {

    /** UUIDs of players currently brandishing a note card. */
    private final Set<UUID> active = ConcurrentHashMap.newKeySet();

    /** Apply a toggle on (active=true) or off (active=false) for one player. */
    public void set(UUID player, boolean on) {
        if (on) {
            active.add(player);
        } else {
            active.remove(player);
        }
    }

    /** Whether this player is currently brandishing. */
    public boolean isActive(UUID player) {
        return player != null && active.contains(player);
    }

    /** Drop a player entirely (e.g. on disconnect) so a stuck flag can't linger. */
    public void clear(UUID player) {
        active.remove(player);
    }
}
