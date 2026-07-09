package com.blinddeafmuted.client;

import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALCapabilities;
import org.lwjgl.openal.EXTEfx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DEAF muffle: an OpenAL low-pass filter attached to each playing sound source while
 * the local player is deaf, so the world sounds like it's heard through a wall / under
 * water — highs gone, bass mostly intact — rather than just turned down.
 *
 * <p>Minecraft has no filter API, so we drive OpenAL EFX directly. All calls here MUST
 * run on the audio thread (they do: {@code SourceMixin} calls us from {@code Source.play/
 * tick}, which Minecraft already dispatches on the audio thread with the AL context
 * current). The filter object is created lazily the first time we need it.
 *
 * <p>The muffle strength comes from the current {@link DeafMuffle} level (cycled live
 * with the H keybind, see {@link DeafHandler}); we push its gains into the filter and
 * re-push them whenever the level changes.
 */
public final class DeafAudioFilter {
    private DeafAudioFilter() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("blind-deaf-muted-deaf-audio");

    /** AL low-pass filter id, 0 = not created yet. */
    private static int filter = 0;
    /** True once we know EFX is unavailable, so we stop retrying. */
    private static boolean unavailable = false;
    /** The gain/gainHf the filter object currently holds (-1 = nothing pushed yet). We cache the
     *  COMPUTED values (not the preset), so a live slider edit to the muffle base re-pushes even
     *  when the preset and the Potion-of-Relief amount are unchanged. */
    private static float appliedGain = -1f;
    private static float appliedGainHf = -1f;

    /** Lazily create the shared low-pass filter object (audio thread only). */
    private static int filter() {
        if (filter != 0 || unavailable) return filter;
        try {
            ALCapabilities caps = AL.getCapabilities();
            if (caps == null || !caps.ALC_EXT_EFX) {
                unavailable = true;
                LOGGER.warn("[deaf] OpenAL EFX not available (ALC_EXT_EFX missing) — muffle disabled, environment will only be range-capped.");
                return 0;
            }
            int f = EXTEfx.alGenFilters();
            int err = AL10.alGetError();
            if (f == 0 || err != AL10.AL_NO_ERROR) {
                unavailable = true;
                LOGGER.warn("[deaf] alGenFilters failed (id={}, err={}) — muffle disabled.", f, err);
                return 0;
            }
            EXTEfx.alFilteri(f, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
            err = AL10.alGetError();
            if (err != AL10.AL_NO_ERROR) {
                unavailable = true;
                LOGGER.warn("[deaf] filter setup failed (err={}) — muffle disabled.", err);
                return 0;
            }
            filter = f;
            LOGGER.info("[deaf] OpenAL low-pass muffle ready (filter id={}).", f);
            return filter;
        } catch (Throwable t) {
            // A missing EFX function pointer throws rather than returning an error code.
            unavailable = true;
            LOGGER.warn("[deaf] EFX low-pass unavailable — muffle disabled.", t);
            return 0;
        }
    }

    /** Push the level's gains into the filter object if they changed since last time. The gains
     *  come from the live base trio ({@link DeafMuffle} × the settings-menu sliders), so a slider
     *  drag lands here on the next source tick. A Potion of Relief lerps them toward 1.0 (fully
     *  clear) by the reduction amount: {@code rem=1} → full muffle, {@code rem=0} → transparent. */
    private static void tune(int f, DeafMuffle level, float rem) {
        // OpenAL's low-pass filter only accepts gain/gainHf in [0,1] (AL_LOWPASS_MAX_GAIN /
        // MAX_GAINHF = 1.0). It can only ATTENUATE, never amplify, so we clamp here. The part of
        // the deafMuffleGain knob ABOVE 1.0 (amplification) is applied elsewhere, on the source
        // volume in SoundSystemMixin#getAdjustedVolume (that value feeds AL_GAIN, which is NOT
        // capped at 1.0). So gain ≤1 → attenuate via this filter; gain >1 → amplify via the source.
        float gain = clamp01(lerp(rem, 1.0f, level.gain()));
        float gainHf = clamp01(lerp(rem, 1.0f, level.gainHf()));
        if (gain == appliedGain && gainHf == appliedGainHf) return;
        EXTEfx.alFilterf(f, EXTEfx.AL_LOWPASS_GAIN, gain);
        EXTEfx.alFilterf(f, EXTEfx.AL_LOWPASS_GAINHF, gainHf);
        LOGGER.info("[deaf] tuned muffle filter id={} to preset {} (gain={}, gainHf={}, relief={})",
                f, level.name(), gain, gainHf, rem);
        appliedGain = gain;
        appliedGainHf = gainHf;
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    /** {@code a} at t=0, {@code b} at t=1. */
    private static float lerp(float t, float a, float b) {
        return a + t * (b - a);
    }

    /**
     * Attach the muffle filter to {@code sourcePointer} while deaf, or clear it otherwise.
     * Cheap enough to call every tick/play; a no-op if EFX isn't supported.
     *
     * <p>OpenAL snapshots filter params at attach time, so re-attaching here each call is
     * what makes an intensity change (H keybind) take effect on new/streaming sounds.
     */
    public static void apply(int sourcePointer, boolean deaf) {
        try {
            if (!deaf) {
                if (unavailable) return; // never attached anything, nothing to clear
                AL10.alSourcei(sourcePointer, EXTEfx.AL_DIRECT_FILTER, EXTEfx.AL_FILTER_NULL);
                swallowError(); // don't let a stale/freed source id leak into Minecraft's next AL check
                return;
            }
            if (unavailable) return;
            int f = filter();
            if (f == 0) return;
            tune(f, DeafState.getMuffle(), ReliefState.disabilityRemaining());
            AL10.alSourcei(sourcePointer, EXTEfx.AL_DIRECT_FILTER, f);
            swallowError(); // ditto — our per-source EFX call must not pollute the global AL error state
        } catch (Throwable t) {
            unavailable = true;
            LOGGER.warn("[deaf] applying muffle failed — disabling.", t);
        }
    }

    /**
     * Consume (and discard) any pending OpenAL error left by our own source calls.
     *
     * <p>OpenAL's error flag is a single global latch. Minecraft polls it after its own
     * operations ({@code AlUtil.checkErrors("Stop"/"Allocate new source"/…)}) and logs
     * whatever it finds. If one of our {@code alSourcei(AL_DIRECT_FILTER, …)} calls above
     * touches a source id that Minecraft has already stopped/freed (a benign race on the
     * audio thread), it sets {@code AL_INVALID_VALUE}; without clearing it here, that error
     * would surface against the next unrelated Minecraft AL call. Reading the error clears it.
     */
    private static void swallowError() {
        AL10.alGetError();
    }
}
