package com.blinddeafmuted.client;

/**
 * Muffle intensity levels for the DEAF effect, cycled live with the deaf keybind (H)
 * for tuning — same idea as {@link BlindMode} on B.
 *
 * <p>A level no longer stores absolute numbers: it holds a single {@link #spread()} factor
 * and derives its three parameters by scaling the shared <b>base trio</b> the player sets from
 * the settings menu ({@code deafMuffleGainHf} / {@code deafMuffleGain} / {@code deafMuffleRange},
 * read live off {@link ClientConfigState}). So ONE trio of sliders drives all four presets and
 * they stay spread apart proportionally: LIGHT = the full base (×1.0), each harsher level a fixed
 * fraction of it down to EXTREME (×0.2). Move a slider → every preset shifts together.
 * <ul>
 *   <li>{@link #gainHf()} — how much of the HIGH frequencies survive (0..1). Lower = more
 *       muffled. The main "hearing loss" knob (OpenAL {@code AL_LOWPASS_GAINHF}).</li>
 *   <li>{@link #gain()} — overall level through the filter (0..1, OpenAL {@code AL_LOWPASS_GAIN}).</li>
 *   <li>{@link #range()} — hearing radius in blocks. Environment sounds fade out over the last
 *       half of it and are inaudible past it (see {@code SoundSystemMixin}), so a deaf player
 *       only hears what's close.</li>
 * </ul>
 */
public enum DeafMuffle {
    // Proportional spread of the base trio (LIGHT = the full base, EXTREME = 20% of it). These
    // fractions reproduce the previously hand-tuned gainHf progression (base 0.0015 → 0.0003) and
    // now spread gain + range the same way, so a single set of ratios keeps every preset in step.
    LIGHT(1.0F),
    MODERATE(0.6667F),
    STRONG(0.4F),
    EXTREME(0.2F);

    private final float spread;

    DeafMuffle(float spread) {
        this.spread = spread;
    }

    /** This preset's fraction of the base trio (1.0 = LIGHT/full, 0.2 = EXTREME). */
    public float spread() {
        return spread;
    }

    public float gainHf() {
        return ClientConfigState.get().deafMuffleGainHf() * spread;
    }

    public float gain() {
        return ClientConfigState.get().deafMuffleGain() * spread;
    }

    /** Hearing radius in blocks: fully audible below half this, silent past it. */
    public float range() {
        return ClientConfigState.get().deafMuffleRange() * spread;
    }
}
