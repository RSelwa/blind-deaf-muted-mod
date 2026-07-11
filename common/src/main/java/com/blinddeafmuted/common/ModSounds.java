package com.blinddeafmuted.common;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;

/**
 * Sound-event registrations shared by both sides. {@link #register()} is called once
 * from the {@code main} entrypoint (runs on both a dedicated server and a physical
 * client) so the events exist with the same id everywhere.
 *
 * <p>The actual audio files + random selection live in the resource pack:
 * {@code assets/blind-deaf-muted/sounds.json} maps each event to its pool of
 * {@code sounds/} .ogg files with per-file weights — the game picks one at random
 * per play, so the code only ever fires the one event.
 */
public final class ModSounds {
    private ModSounds() {}

    /** The relieved-muted "gut noise" pool (farts/burps) — played at a MUTED player
     *  under a Potion of Relief shortly after they start talking. Weighted random
     *  pick per play (see sounds.json). Assigned in {@link #register()}. */
    public static SoundEvent MUTED_RELIEF_NOISE;

    /** The relieved-DEAF tinnitus (ear-ringing) sound — looped in the local deaf
     *  player's own ears while they're under a Potion of Relief (relief's downside for
     *  DEAF, the mirror of the BLIND player's nausea wobble). Played client-side to
     *  self only (see {@code DeafReliefTinnitus}). Assigned in {@link #register()}. */
    public static SoundEvent DEAF_RELIEF_TINNITUS;

    public static void register() {
        // Idempotent, same as ModItems: reachable from more than one entrypoint on a
        // physical client; registering twice would throw.
        if (MUTED_RELIEF_NOISE != null) return;
        MUTED_RELIEF_NOISE = Registry.register(Registries.SOUND_EVENT,
                ModConstants.id("muted_relief_noise"),
                SoundEvent.of(ModConstants.id("muted_relief_noise")));
        DEAF_RELIEF_TINNITUS = Registry.register(Registries.SOUND_EVENT,
                ModConstants.id("deaf_relief_tinnitus"),
                SoundEvent.of(ModConstants.id("deaf_relief_tinnitus")));
    }
}
