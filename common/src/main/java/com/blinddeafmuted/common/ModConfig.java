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
        float deafMegaphoneLowpassHz,
        float deafMegaphoneVolume,
        float mutedLowpassHz,
        float mutedVolume,
        float mutedMegaphoneLowpassHz,
        float mutedMegaphoneVolume,
        // ---- BLIND / DEAF vision + ambient (client-applied) ----
        float blindFogHardEnd,
        float blindFogMediumEnd,
        float deafEnvVolume,
        // ---- timers / chances (server) ----
        float eventMinMinutes,
        float eventMaxMinutes,
        float randomizerChestChance,
        // ---- megaphone timing (server) ----
        float megaphoneBurstSeconds,
        float megaphoneCooldownSeconds,
        // ---- relief potion (server sets, both sides scale) ----
        float reliefReductionPercent,
        float reliefRangeBlocks,
        float reliefDurationSeconds,
        // ---- myopia blur (client-only) ----
        float myopiaBlurStrength,
        float myopiaDarkness,
        // ---- deaf WORLD sound (client-only: blocks/mobs/weather/music, NOT voice) ----
        float deafHearingRange,
        float deafWorldLowpassHz,
        float deafWorldVolume) {

    /** Factory defaults. The DEAF/MUTED voice values are the ones validated with the client in
     *  the {@code feat-muffle-effect} PR (deaf = 3-pole "through a wall" muffle @210 Hz kept
     *  audible at 1.1; deaf+megaphone = clean saturate; muted = faint 1-pole box @300 Hz/0.05;
     *  muted+megaphone = opened @1800 Hz + saturate). See {@code VoiceFx} for how each is used.
     *  NOTE: these apply to a FRESH config only — a server with an existing
     *  {@code config/blind-deaf-muted.json} keeps its saved values (reset in the in-game menu
     *  or delete the file to pick these up). */
    public static final ModConfig DEFAULT = new ModConfig(
            210f, 1.1f, 3000f, 1.1f,
            200f, 2.0f, 1800f, 1.1f,
            2.0f, 7.0f, 1.0f,
            3.0f, 8.0f, 0.55f,
            5.0f, 120.0f,
            0.75f, 8.0f, 120.0f,
            1.0f, 0.15f,
            40.0f, 120.0f, 0.5f);

    public static final PacketCodec<PacketByteBuf, ModConfig> CODEC = PacketCodec.of(
            ModConfig::write, ModConfig::read);

    private static void write(ModConfig c, PacketByteBuf buf) {
        buf.writeFloat(c.deafLowpassHz);
        buf.writeFloat(c.deafVolume);
        buf.writeFloat(c.deafMegaphoneLowpassHz);
        buf.writeFloat(c.deafMegaphoneVolume);
        buf.writeFloat(c.mutedLowpassHz);
        buf.writeFloat(c.mutedVolume);
        buf.writeFloat(c.mutedMegaphoneLowpassHz);
        buf.writeFloat(c.mutedMegaphoneVolume);
        buf.writeFloat(c.blindFogHardEnd);
        buf.writeFloat(c.blindFogMediumEnd);
        buf.writeFloat(c.deafEnvVolume);
        buf.writeFloat(c.eventMinMinutes);
        buf.writeFloat(c.eventMaxMinutes);
        buf.writeFloat(c.randomizerChestChance);
        buf.writeFloat(c.megaphoneBurstSeconds);
        buf.writeFloat(c.megaphoneCooldownSeconds);
        buf.writeFloat(c.reliefReductionPercent);
        buf.writeFloat(c.reliefRangeBlocks);
        buf.writeFloat(c.reliefDurationSeconds);
        buf.writeFloat(c.myopiaBlurStrength);
        buf.writeFloat(c.myopiaDarkness);
        buf.writeFloat(c.deafHearingRange);
        buf.writeFloat(c.deafWorldLowpassHz);
        buf.writeFloat(c.deafWorldVolume);
    }

    private static ModConfig read(PacketByteBuf buf) {
        return new ModConfig(
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    /** Number of tunable fields — the length of {@link #toArray()}. */
    public static final int FIELD_COUNT = 24;

    /** Index into {@link #toArray()} for the deaf hearing-range knob, so the H-key preset
     *  cycler ({@code DeafHandler}) can set it without hard-coding a raw number. */
    public static final int IDX_DEAF_HEARING_RANGE = 21;
    /** Index of the deaf WORLD low-pass cutoff, so the H-key preset cycler can set it too. */
    public static final int IDX_DEAF_WORLD_LOWPASS_HZ = 22;

    /** Flatten to a float[] in declaration order. The slider menu edits this array in place and
     *  rebuilds via {@link #fromArray}, so the field↔index mapping lives ONLY here. Keep this,
     *  {@link #fromArray} and the record in lockstep when adding a field. */
    public float[] toArray() {
        return new float[]{
                deafLowpassHz, deafVolume, deafMegaphoneLowpassHz, deafMegaphoneVolume,
                mutedLowpassHz, mutedVolume, mutedMegaphoneLowpassHz, mutedMegaphoneVolume,
                blindFogHardEnd, blindFogMediumEnd, deafEnvVolume,
                eventMinMinutes, eventMaxMinutes, randomizerChestChance,
                megaphoneBurstSeconds, megaphoneCooldownSeconds,
                reliefReductionPercent, reliefRangeBlocks, reliefDurationSeconds,
                myopiaBlurStrength, myopiaDarkness,
                deafHearingRange, deafWorldLowpassHz, deafWorldVolume};
    }

    /** Inverse of {@link #toArray()}. */
    public static ModConfig fromArray(float[] a) {
        return new ModConfig(
                a[0], a[1], a[2], a[3],
                a[4], a[5], a[6], a[7],
                a[8], a[9], a[10],
                a[11], a[12], a[13],
                a[14], a[15],
                a[16], a[17], a[18],
                a[19], a[20],
                a[21], a[22], a[23]);
    }
}
