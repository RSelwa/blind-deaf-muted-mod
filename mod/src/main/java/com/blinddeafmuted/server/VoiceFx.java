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
 *   <li><b>DEAF → {@link #forDeaf}</b>: the voice is muffled through an "in a box" low-pass
 *       at near-normal loudness (same hearing-loss character the environment gets), NOT
 *       turned down to silence — muffled but present. A speaker holding a megaphone overrides
 *       this: amplified and lightly saturated (no muffle) so it cuts clean through.</li>
 * </ul>
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
    /** Per-sender low-pass filter memory for the MUTED muffle (2 slots: the bare-mute path runs a
     *  2-pole cascade; the megaphone path uses only slot 0), so it's continuous across frames. */
    private final Map<UUID, float[]> muteLowpassState = new ConcurrentHashMap<>();

    /** One decoder per (receiver,sender) stream, for the deaf rebuild path. */
    private final Map<String, OpusDecoder> deafDecoders = new ConcurrentHashMap<>();
    /** One encoder per receiver, for the deaf rebuild path. */
    private final Map<UUID, OpusEncoder> deafEncoders = new ConcurrentHashMap<>();
    /** Per-(receiver,sender) 2-pole low-pass memory (2 slots) for the DEAF "in a box" muffle,
     *  so it's continuous across frames. */
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
    byte[] distort(UUID sender, byte[] opus, boolean megaphone) {
        OpusDecoder decoder = micDecoders.computeIfAbsent(sender, k -> api.createDecoder());
        OpusEncoder encoder = micEncoders.computeIfAbsent(sender, k -> api.createEncoder());
        short[] pcm = decode(decoder, opus);
        if (pcm == null) return null;
        float[] lp = muteLowpassState.computeIfAbsent(sender, k -> new float[2]);
        com.blinddeafmuted.common.ModConfig cfg = config.get();
        if (megaphone) {
            // Megaphone barely helps a muted player: SAME heavy 2-pole box muffle, only a hair
            // higher cutoff + a hair louder than bare — still boxed-in, just slightly clearer.
            lowpass2(pcm, lp, lowpassAlpha(cfg.mutedMegaphoneLowpassHz()));
            scale(pcm, cfg.mutedMegaphoneVolume());
        } else {
            // No megaphone: heavy 2-pole "in a box" muffle — words smother into an
            // unintelligible dull murmur, mild makeup gain so it's still audible.
            lowpass2(pcm, lp, lowpassAlpha(cfg.mutedLowpassHz()));
            scale(pcm, cfg.mutedVolume());
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
    byte[] forDeaf(UUID receiver, UUID sender, byte[] opus, boolean megaphone, boolean speakerMuted) {
        String key = receiver + "|" + sender;
        OpusDecoder decoder = deafDecoders.computeIfAbsent(key, k -> api.createDecoder());
        OpusEncoder encoder = deafEncoders.computeIfAbsent(receiver, k -> api.createEncoder());
        short[] pcm = decode(decoder, opus);
        if (pcm == null) return null;
        float[] lp = deafLowpassState.computeIfAbsent(key, k -> new float[2]);
        com.blinddeafmuted.common.ModConfig cfg = config.get();
        if (megaphone && !speakerMuted) {
            // Megaphone EASES the deafness: the "in a box" muffle opens up a bit (higher cutoff)
            // and comes a touch louder — less painful and clearer, but still muffled, NOT normal.
            lowpass2(pcm, lp, lowpassAlpha(cfg.deafMegaphoneLowpassHz()));
            scale(pcm, cfg.deafMegaphoneVolume());
        } else {
            // Default deaf (and any muted speaker, megaphone or not): NOT turned down — the
            // deafness is a heavy 2-pole "in a box" muffle, then makeup gain so the muffled
            // voice stays present. Muffled/dull, not just quieter.
            lowpass2(pcm, lp, lowpassAlpha(cfg.deafLowpassHz()));
            scale(pcm, cfg.deafVolume());
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

    /** Two cascaded one-pole low-passes (~12 dB/oct) — a much steeper, boxier roll-off than
     *  {@link #lowpassCore}, for the heavy DEAF voice muffle. {@code state} holds both stages'
     *  previous outputs so it's continuous across frames. */
    private static void lowpass2(short[] pcm, float[] state, float alpha) {
        float y1 = state[0];
        float y2 = state[1];
        for (int i = 0; i < pcm.length; i++) {
            y1 += alpha * (pcm[i] - y1);
            y2 += alpha * (y1 - y2);
            pcm[i] = clamp((int) y2);
        }
        state[0] = y1;
        state[1] = y2;
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
