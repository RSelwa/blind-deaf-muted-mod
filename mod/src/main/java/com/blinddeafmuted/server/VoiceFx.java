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
 *   <li><b>MUTED → {@link #distort}</b>: bit-crush + downsample so a muted player's
 *       voice still leaks through but is garbled and barely intelligible (not full
 *       silence like the old cancel).</li>
 *   <li><b>DEAF → {@link #forDeaf}</b>: a non-muted speaker is amplified, dusted with
 *       white noise and hard-clipped — loud enough that a deaf player can hear it, but
 *       a crunchy mess that's still hard to understand. A <em>muted</em> speaker is kept
 *       faint + muffled instead (their mic was already garbled at the source). A speaker
 *       holding a megaphone overrides both: driven up and clipped into the saturated
 *       bullhorn timbre that cuts clean through the deafness.</li>
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

    /** Deaf listener hears everyone at this fraction of normal volume, AND through a
     *  low-pass muffle ({@link #DEAF_LOWPASS_HZ}) — a faint, dull rumble of voices. */
    private static final float DEAF_VOLUME = 0.12f;

    /** Low-pass cutoff for the deaf muffle: frequencies above this are rolled off, so
     *  consonants blur and voices read as a distant murmur. */
    private static final float DEAF_LOWPASS_HZ = 500f;

    /** Extra gain applied before clipping when a speaker uses a megaphone (saturation drive).
     *  The megaphone path is deliberately NOT muffled — that's how it cuts through. */
    private static final float MEGAPHONE_GAIN = 6.0f;

    /** Post-megaphone output is clamped to this fraction of full scale, so the clipped
     *  bullhorn voice is loud but not eardrum-splitting. */
    private static final float MEGAPHONE_CEILING = 0.85f;

    /** MUTED bit-crush depth: keep this many high bits of each sample, zero the rest. */
    private static final int MUTED_KEEP_BITS = 6;

    /** MUTED sample-and-hold factor: repeat 1 sample for this many, dropping the
     *  effective sample rate to ~1/N — adds the crunchy aliased garble. */
    private static final int MUTED_DOWNSAMPLE = 3;

    /** MUTED is also dropped to this fraction of volume — faint AND garbled, so a muted
     *  player's voice barely leaks through and isn't clearly intelligible. */
    private static final float MUTED_VOLUME = 0.35f;

    /** MUTED + megaphone: a LIGHTER garble than the bare {@link #MUTED_KEEP_BITS}/
     *  {@link #MUTED_DOWNSAMPLE}, so the muted player becomes vaguely intelligible — you
     *  can sort of make out words if they speak slowly and clearly, but it's still hard. */
    private static final int MUTED_MEGAPHONE_KEEP_BITS = 9;
    private static final int MUTED_MEGAPHONE_DOWNSAMPLE = 1;

    /** MUTED + megaphone gain + clip ceiling: loud enough to cut through, not a full
     *  saturation blast like the DEAF megaphone path. */
    private static final float MUTED_MEGAPHONE_GAIN = 2.0f;
    private static final float MUTED_MEGAPHONE_CEILING = 0.9f;

    /** DEAF normal (non-muted speaker, no megaphone): drive the voice up so the deaf
     *  player CAN hear it... */
    private static final float DEAF_AMPLIFY_GAIN = 3.0f;
    /** ...but pile on white noise (fraction of full scale)... */
    private static final float DEAF_NOISE = 0.06f;
    /** ...and hard-clip, so it's loud-and-crunchy but still hard to actually understand. */
    private static final float DEAF_CRUNCH_CEILING = 0.9f;

    /** One-pole low-pass coefficient derived from {@link #DEAF_LOWPASS_HZ}:
     *  alpha = w / (w + 1), w = 2π·fc/sr. Smaller alpha = heavier muffle. */
    private static final float DEAF_LOWPASS_ALPHA = lowpassAlpha(DEAF_LOWPASS_HZ);

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

    /** One decoder per (receiver,sender) stream, for the deaf rebuild path. */
    private final Map<String, OpusDecoder> deafDecoders = new ConcurrentHashMap<>();
    /** One encoder per receiver, for the deaf rebuild path. */
    private final Map<UUID, OpusEncoder> deafEncoders = new ConcurrentHashMap<>();
    /** Per-(receiver,sender) low-pass filter memory ({@code [prevOutput]}), so the muffle
     *  is continuous across the 20 ms frames of a stream instead of resetting each frame. */
    private final Map<String, float[]> deafLowpassState = new ConcurrentHashMap<>();

    VoiceFx(VoicechatApi api) {
        this.api = api;
    }

    /**
     * Garble a MUTED speaker's own microphone frame. Always crunched/unintelligible; if
     * {@code megaphone} (the muted speaker is holding one) it's also driven loud + saturated
     * so it cuts through, otherwise dropped near-silent. Returns new Opus bytes to put back
     * on the mic packet, or {@code null} if decoding failed (caller falls back to cancel).
     */
    byte[] distort(UUID sender, byte[] opus, boolean megaphone) {
        OpusDecoder decoder = micDecoders.computeIfAbsent(sender, k -> api.createDecoder());
        OpusEncoder encoder = micEncoders.computeIfAbsent(sender, k -> api.createEncoder());
        short[] pcm = decode(decoder, opus);
        if (pcm == null) return null;
        if (megaphone) {
            // Megaphone in hand: light garble (vaguely intelligible if they speak slowly
            // and clearly) + loud-ish so it cuts through.
            garble(pcm, MUTED_MEGAPHONE_KEEP_BITS, MUTED_MEGAPHONE_DOWNSAMPLE);
            saturate(pcm, MUTED_MEGAPHONE_GAIN, (int) (Short.MAX_VALUE * MUTED_MEGAPHONE_CEILING));
        } else {
            // No megaphone: heavy garble + faint — basically impossible to understand.
            garble(pcm, MUTED_KEEP_BITS, MUTED_DOWNSAMPLE);
            scale(pcm, MUTED_VOLUME);
        }
        return encoder.encode(pcm);
    }

    /**
     * Re-render a voice frame as a DEAF listener should hear it: near-silent normally,
     * or loud and saturated if {@code megaphone} (the speaker is holding one). Returns
     * new Opus bytes for a rebuilt sound packet, or {@code null} on decode failure
     * (caller should fall back to cancelling).
     */
    byte[] forDeaf(UUID receiver, UUID sender, byte[] opus, boolean megaphone, boolean speakerMuted) {
        String key = receiver + "|" + sender;
        OpusDecoder decoder = deafDecoders.computeIfAbsent(key, k -> api.createDecoder());
        OpusEncoder encoder = deafEncoders.computeIfAbsent(receiver, k -> api.createEncoder());
        short[] pcm = decode(decoder, opus);
        if (pcm == null) return null;
        if (megaphone) {
            // Loud and clear-ish — no muffle, so the megaphone cuts through the deafness.
            saturate(pcm, MEGAPHONE_GAIN, (int) (Short.MAX_VALUE * MEGAPHONE_CEILING));
        } else if (speakerMuted) {
            // A muted speaker stays muted even to a deaf ear: their mic was already garbled
            // at the source, so just keep it faint + muffled — don't amplify the garble.
            lowpass(pcm, key);
            scale(pcm, DEAF_VOLUME);
        } else {
            // Everyone else: amplified + noisy + saturated. The deaf player CAN hear them,
            // but it's a loud crunchy mess that's still hard to actually understand.
            addNoise(pcm, DEAF_NOISE);
            saturate(pcm, DEAF_AMPLIFY_GAIN, (int) (Short.MAX_VALUE * DEAF_CRUNCH_CEILING));
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

    /** One-pole IIR low-pass, in place, with state carried per stream so the muffle is
     *  continuous across frames: {@code y += alpha·(x − y)}. */
    private void lowpass(short[] pcm, String streamKey) {
        float[] state = deafLowpassState.computeIfAbsent(streamKey, k -> new float[1]);
        float y = state[0];
        for (int i = 0; i < pcm.length; i++) {
            y += DEAF_LOWPASS_ALPHA * (pcm[i] - y);
            pcm[i] = clamp((int) y);
        }
        state[0] = y;
    }

    /** Boost then hard-clip to {@code ceiling} — the overdrive/saturation that gives
     *  the megaphone its crunchy, cutting timbre. */
    private static void saturate(short[] pcm, float gain, int ceiling) {
        for (int i = 0; i < pcm.length; i++) {
            int v = (int) (pcm[i] * gain);
            if (v > ceiling) v = ceiling;
            else if (v < -ceiling) v = -ceiling;
            pcm[i] = (short) v;
        }
    }

    /** Bit-crush ({@code keepBits} high bits) + sample-and-hold downsample (÷{@code factor}),
     *  in place. Higher keepBits and lower factor = lighter, more intelligible garble. */
    private static void garble(short[] pcm, int keepBits, int factor) {
        int mask = ~((1 << (16 - keepBits)) - 1); // zero the low bits
        short held = 0;
        for (int i = 0; i < pcm.length; i++) {
            if (i % factor == 0) {
                held = (short) (pcm[i] & mask);
            }
            pcm[i] = held;
        }
    }

    /** Add uniform white noise of amplitude {@code amount}·full-scale, in place — the
     *  hiss that makes the amplified DEAF voice loud yet hard to parse. */
    private static void addNoise(short[] pcm, float amount) {
        int amp = (int) (Short.MAX_VALUE * amount);
        if (amp <= 0) return;
        java.util.concurrent.ThreadLocalRandom rnd = java.util.concurrent.ThreadLocalRandom.current();
        for (int i = 0; i < pcm.length; i++) {
            pcm[i] = clamp(pcm[i] + rnd.nextInt(-amp, amp + 1));
        }
    }

    private static short clamp(int v) {
        if (v > Short.MAX_VALUE) return Short.MAX_VALUE;
        if (v < Short.MIN_VALUE) return Short.MIN_VALUE;
        return (short) v;
    }
}
