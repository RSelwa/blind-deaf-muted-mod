package com.blinddeafmuted.server;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Audio effect pipeline for the voice-chat role enforcement.
 *
 * <p>Simple Voice Chat ships an Opus codec ({@link VoicechatApi#createDecoder()} /
 * {@link VoicechatApi#createEncoder()}), so we never touch libopus ourselves. The
 * loop is always the same: decode the 48&nbsp;kHz / 16-bit mono Opus frame to a
 * {@code short[]} of PCM samples, mangle the samples, re-encode.
 *
 * <p>Two effects live here:
 * <ul>
 *   <li><b>MUTED → {@link #distort}</b>: low-pass muffle + low volume so a muted player's
 *       voice still leaks through but sounds like a dull "talking in a box" murmur, barely
 *       intelligible (not full silence like the old cancel). A muted speaker holding a
 *       megaphone instead gets a lighter bit-crush garble, driven loud so it cuts through.</li>
 *   <li><b>DEAF → {@link #forDeaf}</b>: the voice is heavily muffled through a <em>multi-pole</em>
 *       "through a wall" low-pass (3 cascaded stages) and kept audible but dull — smothered,
 *       not silenced, so consonants blur and words get hard to follow. A speaker holding a
 *       megaphone overrides this: amplified and lightly saturated (near-clean, no heavy muffle)
 *       so it cuts through.</li>
 * </ul>
 *
 * <p>The DEAF/MUTED voice character + values here are the ones validated with the client in the
 * {@code feat-muffle-effect} PR; the numeric cutoffs/volumes are read live from {@link ModConfig}
 * (slider menu) with those validated numbers as {@link ModConfig#DEFAULT}.</p>
 *
 * <p><b>Opus is a stateful stream codec</b> — a decoder/encoder carries state between
 * the 20&nbsp;ms frames of one continuous voice stream. So we keep one codec per
 * logical stream (per sender for the mic path; per receiver×sender for the deaf
 * path) rather than recreating them per packet, which would both leak native handles
 * and wreck audio quality. Maps are concurrent because SVC fires packet events off
 * its own threads.
 *
 * <p>TODO: codecs are never closed when a stream ends (player leaves / stops talking).
 * For a 3–6 player co-op session the leak is negligible; if player counts grow, evict
 * on the SVC disconnect event and {@link OpusDecoder#close()} / {@link OpusEncoder#close()}.
 */
final class VoiceFx {

    // ---- Tunables ----------------------------------------------------------
    // The DEAF/MUTED cutoffs + volumes now live in ModConfig (edited live from the client
    // slider menu, read fresh each frame via `config` below) — see the per-effect reads in
    // distort()/forDeaf(). Only the comedic bystander-bullhorn shaping stays hard-coded here;
    // it's an aesthetic effect nobody asked to tune.

    /** Simple Voice Chat decodes to 48 kHz mono PCM. */
    private static final float SAMPLE_RATE = 48_000f;

    /** How many one-pole low-pass stages to cascade for the DEAF muffle (validated: 3 = a firm
     *  but still natural "through a wall" muffle; many more start sounding artificial). The
     *  cutoff itself is the live {@code deafLowpassHz} knob. */
    private static final int DEAF_LOWPASS_POLES = 3;

    /** How many one-pole low-pass stages to cascade for the MUTED mic muffle (no megaphone).
     *  Steeper than the deaf one on purpose: a muted speaker must be genuinely UNINTELLIGIBLE,
     *  not just quiet/dull — 4 poles at the low {@code mutedLowpassHz} cutoff leaves only the bass
     *  rumble of the voice, so you can tell they're talking but not make out words. */
    private static final int MUTED_LOWPASS_POLES = 4;

    /** Target low-pass cutoff a Potion of Relief lerps DEAF/MUTED voice toward — high enough that
     *  voice passes essentially clear. The reduction fraction ({@code reliefReductionPercent})
     *  controls how far toward this + toward full volume the muffle is eased. */
    private static final float RELIEF_CLEAR_HZ = 4000f;

    /** Clip ceiling (fraction of full scale) for the role-enforcement megaphone paths
     *  (deaf-listener + muted-speaker megaphone). Loud peaks saturate a touch for bullhorn bite
     *  without blasting — the validated value. The drive/gain is the per-path megaphone volume
     *  knob. Distinct from the comedic {@link #MEGAPHONE_SATURATE_CEILING} bystander bullhorn. */
    private static final float MEGAPHONE_CEILING = 0.80f;

    /** Bystander bullhorn (a NON-deaf listener hearing a megaphone speaker): band-pass to a
     *  thin, honky "through a horn" timbre, then overdrive hard into a low clip ceiling — a
     *  crunchy comedic bullhorn. The "fun" effect for blind/none listeners. */
    private static final float MEGAPHONE_SATURATE_GAIN = 3.2f;   // makeup for band-pass loss + heavy clip
    private static final float MEGAPHONE_SATURATE_CEILING = 0.5f;
    /** Band-pass edges giving the bullhorn its tinny honk: kill lows below HP + highs above LP,
     *  leaving a mid-focused horn tone before the clip. */
    private static final float MEGAPHONE_HP_HZ = 450f;
    private static final float MEGAPHONE_LP_HZ = 3200f;
    private static final float MEGAPHONE_HP_ALPHA = lowpassAlpha(MEGAPHONE_HP_HZ);
    private static final float MEGAPHONE_LP_ALPHA = lowpassAlpha(MEGAPHONE_LP_HZ);

    /** One-pole IIR coefficient for a cutoff in Hz. Cheap enough (a few FLOPs) to recompute
     *  per frame from the live config, so a slider change takes effect on the very next 20 ms
     *  voice frame with no restart. */
    private static float lowpassAlpha(float cutoffHz) {
        double w = 2.0 * Math.PI * cutoffHz / SAMPLE_RATE;
        return (float) (w / (w + 1.0));
    }

    // ------------------------------------------------------------------------

    private final VoicechatApi api;

    /** Live gameplay config (cutoffs + volumes). Read fresh at the top of each effect call. */
    private final java.util.function.Supplier<com.blinddeafmuted.common.ModConfig> config;

    /** One decoder per sender stream, for the mic (MUTED) path. */
    private final Map<UUID, OpusDecoder> micDecoders = new ConcurrentHashMap<>();
    /** One encoder per sender stream, for the mic (MUTED) path. */
    private final Map<UUID, OpusEncoder> micEncoders = new ConcurrentHashMap<>();
    /** Per-sender low-pass filter memory for the MUTED muffle (1-pole, one slot), continuous
     *  across frames. */
    private final Map<UUID, float[]> muteLowpassState = new ConcurrentHashMap<>();

    /** One decoder per (receiver,sender) stream, for the deaf rebuild path. */
    private final Map<String, OpusDecoder> deafDecoders = new ConcurrentHashMap<>();
    /** One encoder per receiver, for the deaf rebuild path. */
    private final Map<UUID, OpusEncoder> deafEncoders = new ConcurrentHashMap<>();
    /** Per-(receiver,sender) low-pass memory for the DEAF muffle: {@link #DEAF_LOWPASS_POLES}
     *  slots for the cascaded "through a wall" muffle (slot 0 is also reused by the lighter
     *  deaf+megaphone low-pass). Continuous across frames. */
    private final Map<String, float[]> deafLowpassState = new ConcurrentHashMap<>();

    /** One decoder per (receiver,sender) stream, for the non-deaf bystander bullhorn path. */
    private final Map<String, OpusDecoder> bystanderDecoders = new ConcurrentHashMap<>();
    /** One encoder per receiver, for the non-deaf bystander bullhorn path. */
    private final Map<UUID, OpusEncoder> bystanderEncoders = new ConcurrentHashMap<>();
    /** Per-(receiver,sender) band-pass memory (2 slots: HP + LP) for the bullhorn timbre. */
    private final Map<String, float[]> bystanderFilterState = new ConcurrentHashMap<>();

    VoiceFx(VoicechatApi api, java.util.function.Supplier<com.blinddeafmuted.common.ModConfig> config) {
        this.api = api;
        this.config = config;
    }

    /**
     * Mangle a MUTED speaker's own microphone frame. Without a megaphone it's muffled
     * (heavy low-pass) + very faint — a dull "talking in a box" you only catch point-blank.
     * With a megaphone it opens up (light low-pass) and is amplified clean, so the muted
     * player can actually be heard at range — no bit-crush garble, no noise. Returns new
     * Opus bytes to put back on the mic packet, or {@code null} if decoding failed (caller
     * falls back to cancel).
     */
    byte[] distort(UUID sender, byte[] opus, boolean megaphone, boolean relieved) {
        OpusDecoder decoder = micDecoders.computeIfAbsent(sender, k -> api.createDecoder());
        OpusEncoder encoder = micEncoders.computeIfAbsent(sender, k -> api.createEncoder());
        short[] pcm = decode(decoder, opus);
        if (pcm == null) return null;
        float[] lp = muteLowpassState.computeIfAbsent(sender, k -> new float[MUTED_LOWPASS_POLES]);
        com.blinddeafmuted.common.ModConfig cfg = config.get();
        if (megaphone) {
            // Megaphone: a much more open 1-pole low-pass so the voice opens up, then amplified
            // and lightly saturated (bullhorn bite) so a muted player can be heard at range.
            lowpassCore(pcm, lp, lowpassAlpha(cfg.mutedMegaphoneLowpassHz()));
            saturate(pcm, cfg.mutedMegaphoneVolume(), (int) (Short.MAX_VALUE * MEGAPHONE_CEILING));
        } else {
            // No megaphone: a STEEP multi-pole "in a box" muffle so only the bass rumble of the
            // voice survives — you can tell they're speaking but can't make out words. A Potion of
            // Relief on the speaker eases both the muffle (cutoff → clear) and the loudness (→ 1.0)
            // by reliefReductionPercent.
            float lpHz = cfg.mutedLowpassHz();
            float vol = cfg.mutedVolume();
            if (relieved) {
                float r = cfg.reliefReductionPercent();
                lpHz = lerp(lpHz, RELIEF_CLEAR_HZ, r);
                vol = lerp(vol, 1.0f, r);
            }
            float alpha = lowpassAlpha(lpHz);
            for (int stage = 0; stage < MUTED_LOWPASS_POLES; stage++) {
                lowpassStage(pcm, lp, stage, alpha);
            }
            scale(pcm, vol);
        }
        return encoder.encode(pcm);
    }

    /**
     * Re-render a voice frame as a DEAF listener should hear it: near-silent normally,
     * or loud and saturated if the speaker uses a megaphone. Returns new Opus bytes for a
     * rebuilt sound packet, or {@code null} on decode failure (caller falls back to cancel).
     *
     * <p>The megaphone boost only applies when the speaker is NOT muted. A MUTED speaker's
     * disability wins even with a megaphone: their mic is already garbled at the source, and
     * here we keep it faint too, so a muted+megaphone player is still (near) inaudible to a
     * deaf listener — two disabilities don't cancel out into clear audio.
     */
    byte[] forDeaf(UUID receiver, UUID sender, byte[] opus, boolean megaphone, boolean speakerMuted,
                   boolean receiverRelieved) {
        String key = receiver + "|" + sender;
        OpusDecoder decoder = deafDecoders.computeIfAbsent(key, k -> api.createDecoder());
        OpusEncoder encoder = deafEncoders.computeIfAbsent(receiver, k -> api.createEncoder());
        short[] pcm = decode(decoder, opus);
        if (pcm == null) return null;
        float[] lp = deafLowpassState.computeIfAbsent(key, k -> new float[DEAF_LOWPASS_POLES]);
        com.blinddeafmuted.common.ModConfig cfg = config.get();
        if (megaphone && !speakerMuted) {
            // Megaphone cuts through the deafness: a light near-transparent low-pass (so the
            // deafMegaphoneLowpassHz slider still bites if lowered) then amplify + lightly
            // saturate — loud and clear, NOT the heavy wall muffle. (The PR saturated with no
            // low-pass; the high default cutoff keeps it audibly identical while keeping the knob.)
            lowpassStage(pcm, lp, 0, lowpassAlpha(cfg.deafMegaphoneLowpassHz()));
            saturate(pcm, cfg.deafMegaphoneVolume(), (int) (Short.MAX_VALUE * MEGAPHONE_CEILING));
        } else {
            // Default deaf (and any muted speaker, megaphone or not): a heavy MULTI-pole
            // "through a wall" muffle (validated 3 stages @ deafLowpassHz), kept audible via a
            // slight makeup gain — smothered and dull, not silent. A muted speaker's mic is
            // already garbled at source, so this leaves them near-inaudible. One float of filter
            // memory per cascade stage, kept across frames. A Potion of Relief on the LISTENER
            // eases the muffle (cutoff → clear) + loudness (→ 1.0) by reliefReductionPercent.
            float lpHz = cfg.deafLowpassHz();
            float vol = cfg.deafVolume();
            if (receiverRelieved) {
                float r = cfg.reliefReductionPercent();
                lpHz = lerp(lpHz, RELIEF_CLEAR_HZ, r);
                vol = lerp(vol, 1.0f, r);
            }
            for (int stage = 0; stage < DEAF_LOWPASS_POLES; stage++) {
                lowpassStage(pcm, lp, stage, lowpassAlpha(lpHz));
            }
            scale(pcm, vol);
        }
        return encoder.encode(pcm);
    }

    /**
     * Re-render a voice frame for a NON-deaf listener who hears a megaphone speaker: heavy
     * bullhorn saturation, the "fun" comedic distortion (blind/none listeners don't need help
     * hearing, so the megaphone just makes the speaker sound like a distorted bullhorn).
     * Returns new Opus bytes, or {@code null} on decode failure (caller then leaves the packet
     * untouched so the listener still hears the raw voice).
     */
    byte[] forMegaphoneBystander(UUID receiver, UUID sender, byte[] opus) {
        String key = receiver + "|" + sender;
        OpusDecoder decoder = bystanderDecoders.computeIfAbsent(key, k -> api.createDecoder());
        OpusEncoder encoder = bystanderEncoders.computeIfAbsent(receiver, k -> api.createEncoder());
        short[] pcm = decode(decoder, opus);
        if (pcm == null) return null;
        // Band-pass to the thin honky horn tone, THEN overdrive+clip = comedic bullhorn.
        float[] bp = bystanderFilterState.computeIfAbsent(key, k -> new float[2]);
        bandpass(pcm, bp, MEGAPHONE_HP_ALPHA, MEGAPHONE_LP_ALPHA);
        saturate(pcm, MEGAPHONE_SATURATE_GAIN, (int) (Short.MAX_VALUE * MEGAPHONE_SATURATE_CEILING));
        return encoder.encode(pcm);
    }

    // ---- PCM primitives ----------------------------------------------------

    /** Decode one Opus frame, swallowing failures (returns null) so a bad frame can't crash voice. */
    private static short[] decode(OpusDecoder decoder, byte[] opus) {
        try {
            return decoder.decode(opus);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Linear interpolation: {@code a} at t=0, {@code b} at t=1. */
    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /** Linear volume scale with clamping. */
    private static void scale(short[] pcm, float factor) {
        for (int i = 0; i < pcm.length; i++) {
            pcm[i] = clamp((int) (pcm[i] * factor));
        }
    }

    /** One-pole IIR low-pass core: {@code y += alpha·(x − y)}, with {@code state[0]} holding
     *  the previous output so the muffle is continuous across frames. */
    private static void lowpassCore(short[] pcm, float[] state, float alpha) {
        float y = state[0];
        for (int i = 0; i < pcm.length; i++) {
            y += alpha * (pcm[i] - y);
            pcm[i] = clamp((int) y);
        }
        state[0] = y;
    }

    /** One low-pass stage of a cascade: the same one-pole filter as {@link #lowpassCore} but
     *  keeping its memory in {@code state[stage]}, so several stages chain on one buffer for a
     *  steeper (more muffled) roll-off while each stays continuous across frames. Cascading N
     *  stages ≈ N·6 dB/oct — the heavy DEAF "through a wall" muffle uses {@link #DEAF_LOWPASS_POLES}. */
    private static void lowpassStage(short[] pcm, float[] state, int stage, float alpha) {
        float y = state[stage];
        for (int i = 0; i < pcm.length; i++) {
            y += alpha * (pcm[i] - y);
            pcm[i] = clamp((int) y);
        }
        state[stage] = y;
    }

    /** One-pole band-pass: subtract a low-freq tracker (high-pass, edge {@code hpAlpha}) to kill
     *  the lows, then low-pass the result (edge {@code lpAlpha}) to kill the highs — leaving a
     *  mid-focused "through a horn" tone. {@code state[0]} = HP tracker, {@code state[1]} = LP
     *  output; both carry across frames. */
    private static void bandpass(short[] pcm, float[] state, float hpAlpha, float lpAlpha) {
        float lp1 = state[0]; // tracks the lows we subtract off (the high-pass)
        float lp2 = state[1]; // low-pass smoothing of the high-passed signal
        for (int i = 0; i < pcm.length; i++) {
            float x = pcm[i];
            lp1 += hpAlpha * (x - lp1);
            float hp = x - lp1;              // lows removed
            lp2 += lpAlpha * (hp - lp2);     // highs removed
            pcm[i] = clamp((int) lp2);
        }
        state[0] = lp1;
        state[1] = lp2;
    }

    /** Boost then hard-clip to {@code ceiling} — amplification with light saturation only on
     *  the peaks that exceed the ceiling (keep the ceiling high to stay clean). */
    private static void saturate(short[] pcm, float gain, int ceiling) {
        for (int i = 0; i < pcm.length; i++) {
            int v = (int) (pcm[i] * gain);
            if (v > ceiling) v = ceiling;
            else if (v < -ceiling) v = -ceiling;
            pcm[i] = (short) v;
        }
    }

    private static short clamp(int v) {
        if (v > Short.MAX_VALUE) return Short.MAX_VALUE;
        if (v < Short.MIN_VALUE) return Short.MIN_VALUE;
        return (short) v;
    }
}
