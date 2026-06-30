package com.blinddeafmuted.client;

import java.util.Set;

/**
 * Client-side mirror of which players are currently holding the megaphone key, pushed
 * by the server via {@code MegaphoneStatePayload}. {@link MegaphoneFeatureRenderer}
 * reads it (off the render thread, hence {@code volatile}) to decide whether to draw
 * the megaphone-at-the-mouth model on a given player.
 *
 * <p>Keyed by display name to match how {@link RosterState} keys players (the render
 * state exposes {@code name}, not a UUID).
 */
public final class MegaphoneState {
    private MegaphoneState() {}

    private static volatile Set<String> activeNames = Set.of();

    public static void setActive(Set<String> names) {
        activeNames = names;
    }

    public static boolean isActive(String name) {
        return name != null && activeNames.contains(name);
    }
}
