package com.blinddeafmuted.client;

/**
 * Holds the current DEAF muffle intensity ({@link DeafMuffle}). Read every frame by
 * {@link DeafAudioFilter}; cycled by the deaf keybind wired in {@link DeafHandler}.
 * Client-only, single field — a plain volatile is enough (written on the client thread).
 */
public final class DeafState {
    private DeafState() {}

    /** Default muffle. STRONG = "through a wall"; cycle with H to tune. */
    private static volatile DeafMuffle muffle = DeafMuffle.STRONG;

    public static DeafMuffle getMuffle() {
        return muffle;
    }

    /** Advance to the next intensity and return it (wired to a keybind for tuning). */
    public static DeafMuffle cycle() {
        DeafMuffle[] all = DeafMuffle.values();
        muffle = all[(muffle.ordinal() + 1) % all.length];
        return muffle;
    }
}
