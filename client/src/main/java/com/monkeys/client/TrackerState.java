package com.monkeys.client;

import com.monkeys.common.TrackerPayload;

import java.util.List;

/**
 * Holds the latest teammate positions pushed by the server, plus the player's
 * on/off toggle for the tracker HUD.
 *
 * <p>Written from the network thread via {@code client.execute(...)} (so writes land
 * on the client thread) and read by the HUD renderer each frame — a volatile
 * reference to an immutable list is enough.
 */
public final class TrackerState {
    private TrackerState() {}

    /** Default ON: it's a co-op game and the tracker is a quality-of-life aid. */
    private static volatile boolean enabled = true;

    /** Latest teammate entries from the server; empty until the first packet. */
    private static volatile List<TrackerPayload.Entry> entries = List.of();

    public static boolean isEnabled() {
        return enabled;
    }

    /** Flip the HUD on/off (wired to a keybind). Returns the new state. */
    public static boolean toggle() {
        enabled = !enabled;
        return enabled;
    }

    public static List<TrackerPayload.Entry> getEntries() {
        return entries;
    }

    static void setEntries(List<TrackerPayload.Entry> latest) {
        entries = List.copyOf(latest);
    }
}
