package com.blinddeafmuted.common;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;

/**
 * Entity-type registrations shared by both jars (see {@link ModItems} for the
 * why-both-sides note). Registers the thrown {@link RandomizerBottleEntity}.
 */
public final class ModEntities {
    private ModEntities() {}

    /** The thrown Randomizer bottle entity type. Assigned in {@link #register()}. */
    public static EntityType<RandomizerBottleEntity> RANDOMIZER_BOTTLE;

    /** The thrown Potion of Relief entity type. Assigned in {@link #register()}. */
    public static EntityType<ReliefPotionEntity> RELIEF_POTION_BOTTLE;

    public static void register() {
        // Idempotent — see ModItems.register() for why (unified jar, two entrypoints).
        if (RANDOMIZER_BOTTLE != null) return;
        RegistryKey<EntityType<?>> key =
                RegistryKey.of(RegistryKeys.ENTITY_TYPE, ModConstants.id("randomizer_bottle"));
        RANDOMIZER_BOTTLE = Registry.register(Registries.ENTITY_TYPE, key,
                EntityType.Builder.<RandomizerBottleEntity>create(RandomizerBottleEntity::new, SpawnGroup.MISC)
                        .dimensions(0.25f, 0.25f)
                        .maxTrackingRange(4)
                        .trackingTickInterval(10)
                        .build(key));

        RegistryKey<EntityType<?>> reliefKey =
                RegistryKey.of(RegistryKeys.ENTITY_TYPE, ModConstants.id("relief_potion_bottle"));
        RELIEF_POTION_BOTTLE = Registry.register(Registries.ENTITY_TYPE, reliefKey,
                EntityType.Builder.<ReliefPotionEntity>create(ReliefPotionEntity::new, SpawnGroup.MISC)
                        .dimensions(0.25f, 0.25f)
                        .maxTrackingRange(4)
                        .trackingTickInterval(10)
                        .build(reliefKey));
    }
}
