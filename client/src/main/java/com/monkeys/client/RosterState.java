package com.monkeys.client;

import com.monkeys.common.RosterPayload;

import java.util.List;

/**
 * Holds the latest team roster pushed by the server, plus the player's on/off
 * toggle for the leaderboard HUD.
 *
 * <p>Same threading model as {@link TrackerState}: written from the network thread
 * via {@code client.execute(...)} and read by the HUD renderer each frame, so a
 * volatile reference to an immutable list suffices.
 */
public final class RosterState {
    private RosterState() {}

    /** Default ON: knowing who is what is the whole point of the co-op puzzle. */
    private static volatile boolean enabled = true;

    /** Latest roster entries from the server; empty until the first packet. */
    private static volatile List<RosterPayload.Entry> entries = List.of();

    public static boolean isEnabled() {
        return enabled;
    }

    /** Flip the HUD on/off (wired to a keybind). Returns the new state. */
    public static boolean toggle() {
        enabled = !enabled;
        return enabled;
    }

    public static List<RosterPayload.Entry> getEntries() {
        return entries;
    }

    static void setEntries(List<RosterPayload.Entry> latest) {
        entries = List.copyOf(latest);
    }
}
