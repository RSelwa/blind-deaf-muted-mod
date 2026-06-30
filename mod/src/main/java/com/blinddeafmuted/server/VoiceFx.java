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
 *   <li><b>DEAF → {@link #forDeaf}</b>: scale the samples right down so a deaf
 *       listener can only just hear voices — unless the <em>speaker</em> is holding
 *       a megaphone, in which case we drive the gain up and hard-clip, giving the
 *       saturated bullhorn / radio timbre that cuts through.</li>
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

    /** Deaf listener hears everyone at this fraction of normal volume (near-inaudible). */
    private static final float DEAF_VOLUME = 0.12f;

    /** Extra gain applied before clipping when a speaker uses a megaphone (saturation drive). */
    private static final float MEGAPHONE_GAIN = 6.0f;

    /** Post-megaphone output is clamped to this fraction of full scale, so the clipped
     *  bullhorn voice is loud but not eardrum-splitting. */
    private static final float MEGAPHONE_CEILING = 0.85f;

    /** MUTED bit-crush depth: keep this many high bits of each sample, zero the rest. */
    private static final int MUTED_KEEP_BITS = 6;

    /** MUTED sample-and-hold factor: repeat 1 sample for this many, dropping the
     *  effective sample rate to ~1/N — adds the crunchy aliased garble. */
    private static final int MUTED_DOWNSAMPLE = 3;

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

    VoiceFx(VoicechatApi api) {
        this.api = api;
    }

    /**
     * Garble a MUTED speaker's own microphone frame. Returns new Opus bytes to put
     * back on the mic packet, or {@code null} if decoding failed (caller should then
     * fall back to cancelling the packet).
     */
    byte[] distort(UUID sender, byte[] opus) {
        OpusDecoder decoder = micDecoders.computeIfAbsent(sender, k -> api.createDecoder());
        OpusEncoder encoder = micEncoders.computeIfAbsent(sender, k -> api.createEncoder());
        short[] pcm = decode(decoder, opus);
        if (pcm == null) return null;
        garble(pcm);
        return encoder.encode(pcm);
    }

    /**
     * Re-render a voice frame as a DEAF listener should hear it: near-silent normally,
     * or loud and saturated if {@code megaphone} (the speaker is holding one). Returns
     * new Opus bytes for a rebuilt sound packet, or {@code null} on decode failure
     * (caller should fall back to cancelling).
     */
    byte[] forDeaf(UUID receiver, UUID sender, byte[] opus, boolean megaphone) {
        String key = receiver + "|" + sender;
        OpusDecoder decoder = deafDecoders.computeIfAbsent(key, k -> api.createDecoder());
        OpusEncoder encoder = deafEncoders.computeIfAbsent(receiver, k -> api.createEncoder());
        short[] pcm = decode(decoder, opus);
        if (pcm == null) return null;
        if (megaphone) {
            saturate(pcm, MEGAPHONE_GAIN, (int) (Short.MAX_VALUE * MEGAPHONE_CEILING));
        } else {
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

    /** Bit-crush + sample-and-hold downsample, in place, for the MUTED garble. */
    private static void garble(short[] pcm) {
        int mask = ~((1 << (16 - MUTED_KEEP_BITS)) - 1); // zero the low bits
        short held = 0;
        for (int i = 0; i < pcm.length; i++) {
            if (i % MUTED_DOWNSAMPLE == 0) {
                held = (short) (pcm[i] & mask);
            }
            pcm[i] = held;
        }
    }

    private static short clamp(int v) {
        if (v > Short.MAX_VALUE) return Short.MAX_VALUE;
        if (v < Short.MIN_VALUE) return Short.MIN_VALUE;
        return (short) v;
    }
}
