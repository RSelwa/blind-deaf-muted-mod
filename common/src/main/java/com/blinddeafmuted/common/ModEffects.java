package com.blinddeafmuted.common;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;

/**
 * Status-effect registrations shared by both sides. {@link #register()} is called once
 * from the {@code main} entrypoint (runs on both a dedicated server and a physical
 * client) so the effect exists with the same id everywhere.
 *
 * <p><b>Relief</b> is a real vanilla {@link StatusEffect}, so the player gets the full
 * vanilla potion interface for free: the icon in the top-right HUD corner with the
 * built-in duration countdown, the entry in the inventory screen, persistence across
 * relog (saved on the player), and milk-bucket removal. The gameplay scaling itself is
 * done by the code that CHECKS the effect ({@code ReliefManager} server-side for voice,
 * {@code ReliefState} client-side for vision/world-sound) — the effect class carries no
 * behaviour of its own.
 *
 * <p>Icon: {@code assets/blind-deaf-muted/textures/mob_effect/relief.png} (18×18) — the
 * vanilla {@code mob_effects} atlas scans the {@code textures/mob_effect/} directory of
 * every namespace, so a drop-in PNG named after the effect id is all it takes.
 */
public final class ModEffects {
    private ModEffects() {}

    /** "Relief" — disability temporarily reduced (Potion of Relief). Assigned in
     *  {@link #register()}. Kept as a {@link RegistryEntry} because that's what
     *  {@code StatusEffectInstance} and {@code hasStatusEffect} take. */
    public static RegistryEntry<StatusEffect> RELIEF;

    public static void register() {
        // Idempotent, same as ModItems: reachable from more than one entrypoint on a
        // physical client; registering twice would throw.
        if (RELIEF != null) return;
        // StatusEffect's constructor is protected — an empty subclass is the intended
        // way to make a plain marker effect. Beneficial, aqua (matches the potion).
        RELIEF = Registry.registerReference(Registries.STATUS_EFFECT,
                ModConstants.id("relief"),
                new StatusEffect(StatusEffectCategory.BENEFICIAL, 0x4FE0D8) {});
    }
}
