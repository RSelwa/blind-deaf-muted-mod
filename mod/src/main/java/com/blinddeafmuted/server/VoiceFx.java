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
 *   <li><b>DEAF → {@link #forDeaf}</b>: the speaker's voice is heavily low-pass muffled
 *       (like through a wall) AND turned right down to a faint murmur — so even the little
 *       that gets through is smothered, not a clear whisper (that's the deafness). A speaker
 *       holding a megaphone overrides this: amplified, unmuffled and lightly saturated so it's
 *       loud and clear enough to cut through.</li>
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

    // ---- Tunables (tweak by ear) -------------------------------------------

    /** Simple Voice Chat decodes to 48 kHz mono PCM. */
    private static final float SAMPLE_RATE = 48_000f;

    /** Deaf listener hears everyone at this fraction of normal volume — quieter, but still
     *  clearly audible. Bumped a little to make up for the energy the heavier muffle strips.
     *  Raise for louder, lower for more profound deafness. */
    private static final float DEAF_VOLUME = 1.1f;

    /** DEAF low-pass cutoff: a natural "muffled through a wall / pillow" tone. Set LOW enough
     *  to roll off the consonants (fricatives/plosives live in the highs) so words get hard to
     *  make out, while the low vowel energy survives so it stays audible and natural — NOT a
     *  robotic rumble (that's what a near-zero cutoff gives). ~350 Hz is the sweet spot: dull
     *  and hard to follow, still a real voice. Lower = more smothered; higher = clearer. */
    private static final float DEAF_LOWPASS_HZ = 210f;
    private static final float DEAF_LOWPASS_ALPHA = lowpassAlpha(DEAF_LOWPASS_HZ);
    /** How many one-pole low-pass stages to cascade for the DEAF muffle. 3 = a firm but still
     *  natural muffle; many more stages start sounding artificial. */
    private static final int DEAF_LOWPASS_POLES = 3;

    /** Megaphone drive (gain). Modest on purpose: normal speech stays fairly quiet, so a
     *  speaker has to SCREAM (loud input) to actually be heard — and that's when it pushes
     *  into the clip ceiling and saturates a touch. */
    private static final float MEGAPHONE_GAIN = 1.1f;

    /** Post-megaphone clip ceiling (fraction of full scale). Lowered so loud peaks clip and
     *  saturate a LITTLE — gives the bullhorn bite without blasting. */
    private static final float MEGAPHONE_CEILING = 0.80f;

    /** MUTED low-pass cutoff: roll off everything above this so the voice reads as a dull,
     *  muffled "talking in a box" sound — no crispy/aliased garble, just smothered. Lower =
     *  more muffled. */
    private static final float MUTED_LOWPASS_HZ = 300f;
    private static final float MUTED_LOWPASS_ALPHA = lowpassAlpha(MUTED_LOWPASS_HZ);

    /** MUTED bare-mic volume. Set so a muted player (muffled) is heard at roughly the same
     *  faintness a deaf listener hears others ({@link #DEAF_VOLUME}) — a hair higher to make
     *  up for the energy the box muffle strips out. Audible up close, not silent. */
    private static final float MUTED_VOLUME = 0.05f;

    /** MUTED + megaphone low-pass: a much higher cutoff than the bare-mute box muffle, so the
     *  voice opens up and is clear-ish (only lightly filtered), not boxed-in. */
    private static final float MUTED_MEGAPHONE_LOWPASS_HZ = 1800f;
    private static final float MUTED_MEGAPHONE_LOWPASS_ALPHA = lowpassAlpha(MUTED_MEGAPHONE_LOWPASS_HZ);

    /** MUTED + megaphone gain + clip ceiling: same modest drive + low ceiling as the deaf
     *  megaphone — a muted player must speak up to be heard, and loud peaks saturate a touch
     *  (no bit-crush garble, no noise). */
    private static final float MUTED_MEGAPHONE_GAIN = 1.1f;
    private static final float MUTED_MEGAPHONE_CEILING = 0.80f;

    private static float lowpassAlpha(float cutoffHz) {
        double w = 2.0 * Math.PI * cutoffHz / SAMPLE_RATE;
        return (float) (w / (w + 1.0));
    }

    // ------------------------------------------------------------------------

    private final VoicechatApi api;

    /** One decoder per sender stream, for the mic (MUTED) path. */
    private final Map<UUID, OpusDecoder> micDecoders = new ConcurrentHashMap<>();
    /** One encoder per sender stream, for the mic (MUTED) path. */
    private final Map<UUID, OpusEncoder> micEncoders = new ConcurrentHashMap<>();
    /** Per-sender low-pass filter memory for the MUTED muffle, so it's continuous across frames. */
    private final Map<UUID, float[]> muteLowpassState = new ConcurrentHashMap<>();

    /** One decoder per (receiver,sender) stream, for the deaf rebuild path. */
    private final Map<String, OpusDecoder> deafDecoders = new ConcurrentHashMap<>();
    /** One encoder per receiver, for the deaf rebuild path. */
    private final Map<UUID, OpusEncoder> deafEncoders = new ConcurrentHashMap<>();
    /** Per-(receiver,sender) low-pass filter memory for the DEAF muffle, so it's continuous across frames. */
    private final Map<String, float[]> deafLowpassState = new ConcurrentHashMap<>();

    VoiceFx(VoicechatApi api) {
        this.api = api;
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
        float[] lp = muteLowpassState.computeIfAbsent(sender, k -> new float[1]);
        if (megaphone) {
            // Megaphone: barely filtered + amplified clean, so the muted player opens up and
            // can be heard at range — clearer, not boxy, not noisy.
            lowpassCore(pcm, lp, MUTED_MEGAPHONE_LOWPASS_ALPHA);
            saturate(pcm, MUTED_MEGAPHONE_GAIN, (int) (Short.MAX_VALUE * MUTED_MEGAPHONE_CEILING));
        } else {
            // No megaphone: heavy muffle ("in a box") + very faint, so it's only audible to
            // someone standing right next to them.
            lowpassCore(pcm, lp, MUTED_LOWPASS_ALPHA);
            scale(pcm, MUTED_VOLUME);
        }
        return encoder.encode(pcm);
    }

    /**
     * Re-render a voice frame as a DEAF listener should hear it: heavily low-pass muffled and
     * turned right down to a faint murmur, or loud and clear if the speaker uses a megaphone.
     * Returns new Opus bytes for a rebuilt sound packet, or {@code null} on decode failure
     * (caller falls back to cancel).
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
        if (megaphone && !speakerMuted) {
            // Megaphone amplifies the otherwise-faint voice up to a loud, lightly-saturated
            // level so it cuts through the deafness. Clean apart from the hot drive.
            saturate(pcm, MEGAPHONE_GAIN, (int) (Short.MAX_VALUE * MEGAPHONE_CEILING));
        } else {
            // Default deaf (and any muted speaker, megaphone or not): the voice is HEAVILY
            // muffled (multi-pole low-pass, like through a wall) AND turned right down to a
            // faint murmur. A muted speaker's audio is already garbled at source, so this
            // leaves it near-inaudible. The megaphone path above skips the muffle so it stays
            // clear. One float of filter memory per cascade stage, kept across frames.
            float[] lp = deafLowpassState.computeIfAbsent(key, k -> new float[DEAF_LOWPASS_POLES]);
            for (int stage = 0; stage < DEAF_LOWPASS_POLES; stage++) {
                lowpassStage(pcm, lp, stage, DEAF_LOWPASS_ALPHA);
            }
            scale(pcm, DEAF_VOLUME);
        }
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

    /** One low-pass stage of a cascade: same one-pole filter as {@link #lowpassCore} but keeps
     *  its memory in {@code state[stage]}, so several stages can be chained on one buffer for a
     *  steeper (more muffled) roll-off while each stays continuous across frames. */
    private static void lowpassStage(short[] pcm, float[] state, int stage, float alpha) {
        float y = state[stage];
        for (int i = 0; i < pcm.length; i++) {
            y += alpha * (pcm[i] - y);
            pcm[i] = clamp((int) y);
        }
        state[stage] = y;
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
