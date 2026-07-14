package com.blinddeafmuted.server;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Captures and curates "interesting" voice snippets from all players for the
 * DEAF relief ghost-voice replay system.
 *
 * <p>Every microphone frame that passes through the server (all players, not
 * just MUTED) is fed to {@link #onFrame}. This class detects discrete speech
 * <em>segments</em> (bursts of continuous talking separated by silences),
 * <b>scores</b> each completed segment on how "interesting" it sounds (loud
 * exclamations, animated energy, good length), and keeps only the top-scoring
 * segments in a per-player curated store.
 *
 * <p>The scoring requires decoding the Opus frames to PCM (to compute RMS /
 * peak energy), but that decode is only done once per completed segment —
 * during live streaming we store the raw Opus bytes untouched. Segments that
 * don't meet a minimum score are discarded immediately, so we never accumulate
 * boring silence or background hum.
 *
 * <p><b>Threading.</b> {@link #onFrame} is called from Simple Voice Chat's
 * audio threads (off the server tick); it only appends to a lock-free staging
 * area. {@link #tick()} runs on the server tick and finalises completed
 * segments (detects the silence gap, decodes, scores, stores or discards).
 * {@link #getSnippet} is also called from the server tick.
 *
 * <p><b>Memory budget.</b> Each stored segment is a list of Opus frames
 * (~100–200 bytes each, 20 ms of audio per frame). With a cap of
 * {@value #MAX_SEGMENTS_PER_PLAYER} segments per player and a max segment
 * duration, the total memory stays well under 50 MB even for 6 players over
 * a long session.
 */
public final class VoiceSnippetCache {

    private static final Logger LOGGER = LoggerFactory.getLogger("blind-deaf-muted-voice-cache");

    // ---- Segment detection tunables ----------------------------------------

    /** Gap (ms) of no frames before we consider a speech segment finished.
     *  SVC sends a frame every 20 ms while the player is talking; 400 ms ≈
     *  20 missed frames, generous enough for a brief pause mid-sentence. */
    private static final long SILENCE_GAP_MS = 400;

    /** Ignore segments shorter than this (too short to be a meaningful phrase). */
    private static final int MIN_SEGMENT_FRAMES = 25;  // 25 × 20ms = 500ms

    /** Cap segments longer than this (monologues are boring for replay). */
    private static final int MAX_SEGMENT_FRAMES = 250; // 250 × 20ms = 5s

    // ---- Scoring tunables --------------------------------------------------

    /** Minimum RMS energy (on a 0..1 scale, where 1 = Short.MAX_VALUE) for a
     *  segment to be worth keeping. Filters out whispers and background noise.
     *  Set VERY low: we want to capture normal conversation, not just screams. */
    private static final float MIN_RMS_THRESHOLD = 0.003f;

    /** Crest factor (peak / RMS) bonus threshold: segments with a high crest
     *  factor have sudden exclamations (shouts, reactions) — more interesting. */
    private static final float CREST_BONUS_THRESHOLD = 3.0f;

    // ---- Storage caps ------------------------------------------------------

    /** Max curated segments per player. When full, the lowest-scored segment is
     *  evicted to make room. 60 segments × ~3s avg × ~150 B/frame × 150 frames
     *  ≈ ~1.35 MB/player. */
    static final int MAX_SEGMENTS_PER_PLAYER = 60;

    /** Max age of a stored segment before it's evicted (15 minutes). */
    private static final long MAX_AGE_MS = 15L * 60L * 1000L;

    // ---- State -------------------------------------------------------------

    /** Per-player staging: Opus frames being accumulated for the current speech
     *  segment. Written from audio threads (putIfAbsent + synchronized on the
     *  list), read/cleared from the server tick. */
    private final ConcurrentHashMap<UUID, StagingSegment> staging = new ConcurrentHashMap<>();

    /** Per-player curated store (server tick only — no concurrent access). */
    private final HashMap<UUID, List<ScoredSegment>> store = new HashMap<>();

    /** The SVC API, for creating Opus decoders on demand. Set once at init. */
    private volatile VoicechatApi api;

    // ---- Data types --------------------------------------------------------

    /** A segment being built (frames still arriving from the audio thread). */
    private static final class StagingSegment {
        final List<byte[]> frames = Collections.synchronizedList(new ArrayList<>());
        volatile long lastFrameMs = System.currentTimeMillis();
    }

    /** A completed, scored, and accepted segment — stored for replay. */
    static final class ScoredSegment {
        final UUID speaker;
        final short[] pcm;       // decoded PCM, ready for AudioPlayer
        final long capturedAtMs;
        final float score;
        final int durationMs;

        ScoredSegment(UUID speaker, short[] pcm, long capturedAtMs, float score, int durationMs) {
            this.speaker = speaker;
            this.pcm = pcm;
            this.capturedAtMs = capturedAtMs;
            this.score = score;
            this.durationMs = durationMs;
        }
    }

    // ---- Public API --------------------------------------------------------

    /** Provide the SVC API instance (for Opus decoding). Called once at init. */
    public void bindApi(VoicechatApi api) {
        this.api = api;
    }

    /**
     * Record one Opus frame from a speaker. Called from SVC audio threads —
     * MUST NOT touch entity/world state.
     */
    public void onFrame(UUID speaker, byte[] opusData) {
        // IMPORTANT: copy the byte array — SVC may reuse the buffer after
        // the event handler returns, so our stored reference would point at
        // overwritten data.
        byte[] copy = Arrays.copyOf(opusData, opusData.length);
        StagingSegment seg = staging.computeIfAbsent(speaker, k -> new StagingSegment());
        seg.frames.add(copy);
        seg.lastFrameMs = System.currentTimeMillis();

        // Hard cap: if a segment exceeds MAX_SEGMENT_FRAMES, trim older frames
        // so we keep only the tail (most recent speech). This prevents a very
        // long monologue from eating memory in the staging area.
        if (seg.frames.size() > MAX_SEGMENT_FRAMES) {
            synchronized (seg.frames) {
                while (seg.frames.size() > MAX_SEGMENT_FRAMES) {
                    seg.frames.remove(0);
                }
            }
        }
    }

    /**
     * Finalise completed segments. Server tick only (END_SERVER_TICK).
     * Detects silence gaps, scores segments, stores or discards them.
     */
    public void tick() {
        if (staging.isEmpty() && store.isEmpty()) return;
        long now = System.currentTimeMillis();

        // 1. Check staging for completed segments (silence gap elapsed).
        Iterator<Map.Entry<UUID, StagingSegment>> it = staging.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, StagingSegment> entry = it.next();
            StagingSegment seg = entry.getValue();
            if (now - seg.lastFrameMs < SILENCE_GAP_MS) continue;

            // Segment complete — remove from staging.
            it.remove();

            List<byte[]> frames;
            synchronized (seg.frames) {
                frames = new ArrayList<>(seg.frames);
            }

            UUID speaker = entry.getKey();

            // Too short? Discard.
            if (frames.size() < MIN_SEGMENT_FRAMES) {
                LOGGER.info("[voice-cache] Discarded segment from {} — too short ({} frames = {}ms)",
                        speaker, frames.size(), frames.size() * 20);
                continue;
            }

            // Decode and score.
            processCompletedSegment(speaker, frames, now);
        }

        // 2. Evict expired segments from the store.
        for (List<ScoredSegment> segments : store.values()) {
            segments.removeIf(s -> now - s.capturedAtMs > MAX_AGE_MS);
        }
    }

    /**
     * Pick a random curated snippet suitable for replay to the given deaf player.
     * Returns {@code null} if no material is available.
     *
     * @param excludeSelf  the deaf player's UUID (don't replay their own voice)
     */
    public ScoredSegment getSnippet(UUID excludeSelf) {
        // Collect all eligible segments from all players except self.
        List<ScoredSegment> pool = new ArrayList<>();
        for (Map.Entry<UUID, List<ScoredSegment>> entry : store.entrySet()) {
            if (entry.getKey().equals(excludeSelf)) continue;
            pool.addAll(entry.getValue());
        }
        if (pool.isEmpty()) return null;

        // Weighted random pick: higher-scored segments are more likely.
        // Simple approach: pick from top half (sorted by score desc).
        pool.sort(Comparator.comparingDouble((ScoredSegment s) -> s.score).reversed());
        int topN = Math.max(1, pool.size() / 2);
        return pool.get(ThreadLocalRandom.current().nextInt(topN));
    }

    /** Whether there is at least one usable segment from anyone (excluding the given player). */
    public boolean hasMaterial(UUID excludeSelf) {
        for (Map.Entry<UUID, List<ScoredSegment>> entry : store.entrySet()) {
            if (entry.getKey().equals(excludeSelf)) continue;
            if (!entry.getValue().isEmpty()) return true;
        }
        return false;
    }

    /** Drop a leaver's data (staging + store). */
    public void clear(UUID player) {
        staging.remove(player);
        store.remove(player);
    }

    // ---- Internals ---------------------------------------------------------

    private void processCompletedSegment(UUID speaker, List<byte[]> opusFrames, long now) {
        VoicechatApi svcApi = this.api;
        if (svcApi == null) {
            LOGGER.warn("[voice-cache] SVC API not bound yet — cannot process segment");
            return;
        }

        // Decode all frames to one PCM buffer.
        OpusDecoder decoder = svcApi.createDecoder();
        if (decoder == null) {
            LOGGER.warn("[voice-cache] Failed to create Opus decoder");
            return;
        }

        try {
            List<short[]> pcmFrames = new ArrayList<>(opusFrames.size());
            int totalSamples = 0;
            int failedFrames = 0;
            for (byte[] opus : opusFrames) {
                try {
                    short[] pcm = decoder.decode(opus);
                    if (pcm != null) {
                        pcmFrames.add(pcm);
                        totalSamples += pcm.length;
                    } else {
                        failedFrames++;
                    }
                } catch (RuntimeException e) {
                    failedFrames++;
                }
            }
            if (pcmFrames.isEmpty()) {
                LOGGER.warn("[voice-cache] All {} frames failed to decode for {}",
                        opusFrames.size(), speaker);
                return;
            }
            if (failedFrames > 0) {
                LOGGER.info("[voice-cache] {} of {} frames failed to decode for {}",
                        failedFrames, opusFrames.size(), speaker);
            }

            // Concatenate into a single PCM buffer.
            short[] fullPcm = new short[totalSamples];
            int offset = 0;
            for (short[] frame : pcmFrames) {
                System.arraycopy(frame, 0, fullPcm, offset, frame.length);
                offset += frame.length;
            }

            // Score it.
            float score = scoreSegment(fullPcm, opusFrames.size());
            int durationMs = opusFrames.size() * 20;
            if (score <= 0f) {
                LOGGER.info("[voice-cache] Discarded segment from {} — score too low ({}ms, score={})",
                        speaker, durationMs, score);
                return;
            }

            ScoredSegment scored = new ScoredSegment(speaker, fullPcm, now, score, durationMs);

            // Store it (evict lowest if full).
            List<ScoredSegment> playerStore = store.computeIfAbsent(speaker, k -> new ArrayList<>());
            if (playerStore.size() >= MAX_SEGMENTS_PER_PLAYER) {
                // Find and remove the lowest-scored segment.
                int lowestIdx = 0;
                float lowestScore = playerStore.get(0).score;
                for (int i = 1; i < playerStore.size(); i++) {
                    if (playerStore.get(i).score < lowestScore) {
                        lowestScore = playerStore.get(i).score;
                        lowestIdx = i;
                    }
                }
                // Only evict if the new segment is better.
                if (scored.score <= lowestScore) return;
                playerStore.remove(lowestIdx);
            }
            playerStore.add(scored);
            LOGGER.info("[voice-cache] Stored segment from {} — {}ms, score={}, store size={}",
                    speaker, durationMs, score, playerStore.size());
        } finally {
            decoder.close();
        }
    }

    /**
     * Score a segment based on its audio characteristics. Higher = more interesting.
     *
     * <p>Heuristics:
     * <ul>
     *   <li><b>RMS energy</b>: must exceed {@link #MIN_RMS_THRESHOLD} or score = 0</li>
     *   <li><b>Duration sweet spot</b>: 0.8–3s is the most interesting range (short
     *       exclamations, reactions); longer is OK but scores lower per second</li>
     *   <li><b>Crest factor</b> (peak / RMS): high = shouts/reactions = bonus</li>
     *   <li><b>Energy variance</b>: animated speech (dynamic volume) scores higher</li>
     * </ul>
     *
     * @return score ≥ 0, or 0 if not worth keeping
     */
    private static float scoreSegment(short[] pcm, int frameCount) {
        if (pcm.length == 0) return 0f;

        // Compute RMS and peak.
        double sumSquared = 0;
        int peak = 0;
        for (short sample : pcm) {
            int abs = Math.abs(sample);
            sumSquared += (double) sample * sample;
            if (abs > peak) peak = abs;
        }
        float rms = (float) Math.sqrt(sumSquared / pcm.length) / Short.MAX_VALUE;
        float peakNorm = (float) peak / Short.MAX_VALUE;

        // Gate: too quiet = not interesting.
        if (rms < MIN_RMS_THRESHOLD) return 0f;

        // Base score = RMS energy (louder = more interesting).
        float score = rms * 10f;

        // Crest factor bonus: high crest = exclamation/shout.
        float crest = peakNorm / rms;
        if (crest > CREST_BONUS_THRESHOLD) {
            score += (crest - CREST_BONUS_THRESHOLD) * 0.5f;
        }

        // Duration bonus: sweet spot 0.8–3s.
        int durationMs = frameCount * 20;
        if (durationMs >= 800 && durationMs <= 3000) {
            score *= 1.3f; // sweet spot bonus
        } else if (durationMs > 4000) {
            score *= 0.8f; // long monologue penalty
        }

        // Energy variance: compute RMS per-frame (rough), check std dev.
        if (frameCount >= 5) {
            int samplesPerFrame = pcm.length / frameCount;
            float[] frameEnergies = new float[frameCount];
            for (int f = 0; f < frameCount; f++) {
                double sum = 0;
                int start = f * samplesPerFrame;
                int end = Math.min(start + samplesPerFrame, pcm.length);
                for (int i = start; i < end; i++) {
                    sum += (double) pcm[i] * pcm[i];
                }
                frameEnergies[f] = (float) Math.sqrt(sum / (end - start));
            }
            float mean = 0;
            for (float e : frameEnergies) mean += e;
            mean /= frameEnergies.length;
            float variance = 0;
            for (float e : frameEnergies) variance += (e - mean) * (e - mean);
            variance /= frameEnergies.length;
            float stdDev = (float) Math.sqrt(variance) / Short.MAX_VALUE;

            // Higher variance = more dynamic speech = more interesting.
            score += stdDev * 20f;
        }

        return score;
    }
}
