package com.blinddeafmuted.client;

import java.util.Set;

/**
 * Client mirror of who is brandishing a note card (Sea-of-Thieves style show-to-others).
 *
 * <p>Two pieces:
 * <ul>
 *   <li>{@link #localActive} — the LOCAL player's own toggle, flipped instantly on right-click
 *       (so our own render reacts without waiting for the server round-trip) and sent to the
 *       server via {@code CardBrandishPayload}.</li>
 *   <li>{@link #others} — the set of OTHER players brandishing, mirrored from the server's
 *       {@code CardBrandishStatePayload}.</li>
 * </ul>
 *
 * <p>The SoT inversion lives in the renderers, which read {@link #isBrandishing}:
 * brandishing → the card face turns OUTWARD (others read it, the writer's private HUD hides);
 * not brandishing → the face turns toward the writer (only they read it, via {@link NoteCardHud}).
 */
public final class CardBrandishState {
    private CardBrandishState() {}

    private static volatile boolean localActive = false;
    private static volatile Set<String> others = Set.of();

    /** Flip the local player's brandish toggle; returns the new value. */
    public static boolean toggleLocal() {
        localActive = !localActive;
        return localActive;
    }

    /** The local player's own brandish state. */
    public static boolean localActive() {
        return localActive;
    }

    /** Reset the local toggle (e.g. when the card leaves the hand or we disconnect). */
    public static void clearLocal() {
        localActive = false;
    }

    /** Mirror the server's set of OTHER players currently brandishing. */
    public static void setOthers(Set<String> names) {
        others = names;
    }

    /**
     * Whether the named player's card face should point OUTWARD (toward viewers). For the
     * local player we trust our own instant toggle; for everyone else we trust the server set.
     */
    public static boolean isBrandishing(String name) {
        var client = net.minecraft.client.MinecraftClient.getInstance();
        if (client.player != null && client.player.getName().getString().equals(name)) {
            return localActive;
        }
        return others.contains(name);
    }
}
