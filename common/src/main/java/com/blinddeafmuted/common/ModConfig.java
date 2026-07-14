package com.blinddeafmuted.common;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;

/**
 * All live-tunable gameplay parameters, in one immutable snapshot.
 *
 * <p>These used to be scattered {@code static final} constants across {@code VoiceFx},
 * {@code BackgroundRendererMixin}, {@code SoundSystemMixin} and {@code RandomEventManager},
 * each needing a rebuild + restart to change. They now live here so the server can hold a
 * single live copy, players can nudge them from an in-game slider menu, and the new value
 * takes effect immediately on every side — no restart.
 *
 * <p>Flow: the server owns the authoritative {@code ModConfig} (persisted to JSON), broadcasts
 * it via {@link ConfigPayload} on join and after every change; a client edits a copy in the
 * slider menu and sends it back via {@link ConfigUpdatePayload}. Server-enforced audio
 * ({@code VoiceFx}) reads the server's live copy; pure-client vision knobs (fog / env volume)
 * read the client's mirror.
 *
 * <p><b>Adding a field:</b> add it to the record, {@link #DEFAULT}, the {@link #CODEC} (same
 * order both ways), the JSON (de)serialiser in {@code ConfigManager}, and a slider in
 * {@code ConfigScreen}. Bump {@link ModConstants#PROTOCOL_VERSION} — the wire format changed.
 *
 * <p>Frequencies are low-pass cutoffs in Hz; volumes are linear gain multipliers (1.0 =
 * unchanged); fog ends are in blocks; event intervals are in minutes; chances are 0..1.
 */
public record ModConfig(
        // ---- DEAF / MUTED voice (server-enforced, VoiceFx) ----
        float deafLowpassHz,
        float deafVolume,
        float mutedLowpassHz,
        float mutedVolume,
        // ---- fog/ambient (client) ----+ ambient (client-applied) ----
        float blindFogHardEnd,
        float blindFogMediumEnd,
        float deafEnvVolume,
        // ---- timers / chances (server) ----
        float eventAutoRerollEnabled,
        float eventMinMinutes,
        float eventMaxMinutes,
        float randomizerChestChance,
        // ---- megaphone timing (server) ----
        float megaphoneBurstSeconds,
        float megaphoneCooldownSeconds,
        // ---- relief potion (server sets, both sides scale) ----
        float reliefRangeBlocks,
        float reliefDurationSeconds,
        // ---- myopia blur (client-only) ----
        float myopiaBlurStrength,
        float myopiaDarkness,
        // ---- deaf WORLD muffle base (client-only; the DeafMuffle H-key presets spread from these:
        //      LIGHT = the full base, harsher presets a fixed proportional fraction of it) ----
        float deafMuffleGainHf,
        float deafMuffleGain,
        float deafMuffleRange,
        // ---- deaf relief tinnitus (client-only; looped ear-ringing while a DEAF player is relieved) ----
        float deafReliefTinnitusVolume,
        float deafReliefTinnitusFadeSeconds,
        float deafReliefTinnitusDurationSeconds,
        // ---- ghost voices (server-only; the voices heard when relieved) ----
        float deafReliefVoicesIntervalMinSeconds,
        float deafReliefVoicesIntervalMaxSeconds,
        float deafReliefVoicesNearbyRangeBlocks,
        // ---- muted relief noise (server-only; the gut noises when a MUTED player speaks) ----
        float mutedReliefNoiseIntervalMinSeconds,
        float mutedReliefNoiseIntervalMaxSeconds,
        float mutedReliefNoiseVolume,
        // ---- blind relief nausea (client-only; the screen wobble) ----
        float blindReliefNauseaStrength) {

    /** Factory defaults. The DEAF/MUTED voice values are the ones validated with the client in
     *  the {@code feat-muffle-effect} PR (deaf = 3-pole "through a wall" muffle @210 Hz kept
     *  audible at 1.1; deaf+megaphone = clean saturate; muted = faint 1-pole box @300 Hz/0.05;
     *  muted+megaphone = opened @1800 Hz + saturate). See {@code VoiceFx} for how each is used.
     *  NOTE: these apply to a FRESH config only — a server with an existing
     *  {@code config/blind-deaf-muted.json} keeps its saved values (reset in the in-game menu
     *  or delete the file to pick these up). */
    public static final ModConfig DEFAULT = new ModConfig(
            /* deafLowpassHz            */ 64f,
            /* deafVolume               */ 17f,
            /* mutedLowpassHz           */ 100f,
            /* mutedVolume              */ 30.0f,
            /* blindFogHardEnd          */ 2.0f,
            /* blindFogMediumEnd        */ 7.0f,
            /* deafEnvVolume            */ 1.0f,
            /* eventAutoRerollEnabled   */ 0.0f,
            /* eventMinMinutes          */ 3.0f,
            /* eventMaxMinutes          */ 8.0f,
            /* randomizerChestChance    */ 0.55f,
            /* megaphoneBurstSeconds    */ 5.0f,
            /* megaphoneCooldownSeconds */ 120.0f,
            /* reliefRangeBlocks        */ 8.0f,
            /* reliefDurationSeconds    */ 30.0f,
            /* myopiaBlurStrength       */ 2.0f,
            /* myopiaDarkness           */ 0.12f,
            /* deafMuffleGainHf         */ 0.0015f,
            /* deafMuffleGain           */ 1.0f,
            /* deafMuffleRange          */ 10.0f,
            /* deafReliefTinnitusVolume */ 0.03f,
            /* deafReliefTinnitusFadeSeconds     */ 0.5f,
            /* deafReliefTinnitusDurationSeconds */ 3.0f,
            /* deafReliefVoicesIntervalMinSeconds */ 4.0f,
            /* deafReliefVoicesIntervalMaxSeconds */ 10.0f,
            /* deafReliefVoicesNearbyRangeBlocks  */ 30.0f,
            /* mutedReliefNoiseIntervalMinSeconds */ 3.5f,
            /* mutedReliefNoiseIntervalMaxSeconds */ 3.7f,
            /* mutedReliefNoiseVolume             */ 1.0f,
            /* blindReliefNauseaStrength          */ 0.3f);

    public static final PacketCodec<PacketByteBuf, ModConfig> CODEC = PacketCodec.of(
            ModConfig::write, ModConfig::read);

    private static void write(ModConfig c, PacketByteBuf buf) {
        buf.writeFloat(c.deafLowpassHz);
        buf.writeFloat(c.deafVolume);
        buf.writeFloat(c.mutedLowpassHz);
        buf.writeFloat(c.mutedVolume);
        buf.writeFloat(c.blindFogHardEnd);
        buf.writeFloat(c.blindFogMediumEnd);
        buf.writeFloat(c.deafEnvVolume);
        buf.writeFloat(c.eventAutoRerollEnabled);
        buf.writeFloat(c.eventMinMinutes);
        buf.writeFloat(c.eventMaxMinutes);
        buf.writeFloat(c.randomizerChestChance);
        buf.writeFloat(c.megaphoneBurstSeconds);
        buf.writeFloat(c.megaphoneCooldownSeconds);
        buf.writeFloat(c.reliefRangeBlocks);
        buf.writeFloat(c.reliefDurationSeconds);
        buf.writeFloat(c.myopiaBlurStrength);
        buf.writeFloat(c.myopiaDarkness);
        buf.writeFloat(c.deafMuffleGainHf);
        buf.writeFloat(c.deafMuffleGain);
        buf.writeFloat(c.deafMuffleRange);
        buf.writeFloat(c.deafReliefTinnitusVolume);
        buf.writeFloat(c.deafReliefTinnitusFadeSeconds);
        buf.writeFloat(c.deafReliefTinnitusDurationSeconds);
        buf.writeFloat(c.deafReliefVoicesIntervalMinSeconds);
        buf.writeFloat(c.deafReliefVoicesIntervalMaxSeconds);
        buf.writeFloat(c.deafReliefVoicesNearbyRangeBlocks);
        buf.writeFloat(c.mutedReliefNoiseIntervalMinSeconds);
        buf.writeFloat(c.mutedReliefNoiseIntervalMaxSeconds);
        buf.writeFloat(c.mutedReliefNoiseVolume);
        buf.writeFloat(c.blindReliefNauseaStrength);
    }

    private static ModConfig read(PacketByteBuf buf) {
        return new ModConfig(
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat());
    }

    /** Number of tunable fields — the length of {@link #toArray()}. */
    public static final int FIELD_COUNT = 30;

    /** Flatten to a float[] in declaration order. The slider menu edits this array in place and
     *  rebuilds via {@link #fromArray}, so the field↔index mapping lives ONLY here. Keep this,
     *  {@link #fromArray} and the record in lockstep when adding a field. */
    public float[] toArray() {
        return new float[]{
                deafLowpassHz, deafVolume,
                mutedLowpassHz, mutedVolume,
                blindFogHardEnd, blindFogMediumEnd, deafEnvVolume,
                eventAutoRerollEnabled, eventMinMinutes, eventMaxMinutes, randomizerChestChance,
                megaphoneBurstSeconds, megaphoneCooldownSeconds,
                reliefRangeBlocks, reliefDurationSeconds,
                myopiaBlurStrength, myopiaDarkness,
                deafMuffleGainHf, deafMuffleGain, deafMuffleRange,
                deafReliefTinnitusVolume, deafReliefTinnitusFadeSeconds, deafReliefTinnitusDurationSeconds,
                deafReliefVoicesIntervalMinSeconds, deafReliefVoicesIntervalMaxSeconds, deafReliefVoicesNearbyRangeBlocks,
                mutedReliefNoiseIntervalMinSeconds, mutedReliefNoiseIntervalMaxSeconds, mutedReliefNoiseVolume,
                blindReliefNauseaStrength};
    }

    /** Inverse of {@link #toArray()}. */
    public static ModConfig fromArray(float[] a) {
        return new ModConfig(
                a[0], a[1], a[2], a[3],
                a[4], a[5], a[6],
                a[7], a[8], a[9], a[10],
                a[11], a[12],
                a[13], a[14],
                a[15], a[16],
                a[17], a[18], a[19],
                a[20], a[21], a[22],
                a[23], a[24], a[25],
                a[26], a[27], a[28],
                a[29]);
    }
}
