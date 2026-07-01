package com.blinddeafmuted.client;

/**
 * Muffle intensity levels for the DEAF effect, cycled live with the deaf keybind (H)
 * for tuning — same idea as {@link BlindMode} on B. Each carries the two OpenAL low-pass
 * gains {@link DeafAudioFilter} feeds the filter:
 * <ul>
 *   <li>{@link #gainHf()} — how much of the HIGH frequencies survive (0..1). Lower =
 *       more muffled. This is the main "hearing loss" knob.</li>
 *   <li>{@link #gain()} — overall level through the filter. Kept near 1 so bass stays
 *       strong; only the harsher levels trim it a touch.</li>
 *   <li>{@link #range()} — hearing radius in blocks. Environment sounds fade out over
 *       the last half of it and are fully inaudible past it (see {@code SoundSystemMixin}),
 *       so a deaf player only hears what's close. Harsher level = shorter range.</li>
 * </ul>
 */
public enum DeafMuffle {
    LIGHT(0.35F, 1.0F, 40.0F),
    MODERATE(0.18F, 1.0F, 24.0F),
    STRONG(0.08F, 0.95F, 14.0F),
    EXTREME(0.02F, 0.85F, 8.0F);

    private final float gainHf;
    private final float gain;
    private final float range;

    DeafMuffle(float gainHf, float gain, float range) {
        this.gainHf = gainHf;
        this.gain = gain;
        this.range = range;
    }

    public float gainHf() {
        return gainHf;
    }

    public float gain() {
        return gain;
    }

    /** Hearing radius in blocks: fully audible below half this, silent past it. */
    public float range() {
        return range;
    }
}
