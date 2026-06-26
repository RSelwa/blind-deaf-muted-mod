package com.monkeys.common;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;

import java.util.function.Consumer;

/**
 * The thrown "Randomizer" bottle — a splash-potion-style projectile that, when it
 * shatters, re-rolls everyone's role (the chaos item from the design). Modelled on
 * vanilla {@code ExperienceBottleEntity}.
 *
 * <p>The shatter <em>effect</em> (re-rolling roles) is server-only game logic that
 * lives in the {@code server} module, which {@code common} can't reference. So the
 * server installs its behaviour into {@link #SHATTER_HANDLER} at init; this entity
 * just invokes it on impact. On a client (or before the server sets it) the handler
 * is the no-op default — harmless, because the impact logic only runs server-side.
 */
public class RandomizerBottleEntity extends ThrownItemEntity {

    /**
     * Server-installed callback invoked (server-side only) when a bottle shatters.
     * Defaults to a no-op; {@code MonkeysServer} replaces it with the role re-roll.
     */
    public static Consumer<RandomizerBottleEntity> SHATTER_HANDLER = entity -> {};

    public RandomizerBottleEntity(EntityType<? extends RandomizerBottleEntity> type, World world) {
        super(type, world);
    }

    public RandomizerBottleEntity(World world, LivingEntity owner, ItemStack stack) {
        super(ModEntities.RANDOMIZER_BOTTLE, owner, world, stack);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.RANDOMIZER;
    }

    /** A gentle arc like the experience bottle, rather than a flat throw. */
    @Override
    protected double getGravity() {
        return 0.07;
    }

    @Override
    protected void onCollision(HitResult hitResult) {
        super.onCollision(hitResult);
        // Effects + game logic are server-authoritative; the client just sees the
        // entity vanish (the server spawns the particles, which sync to everyone).
        if (getWorld() instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(ParticleTypes.WITCH,
                    getX(), getY() + 0.2, getZ(), 30, 0.25, 0.25, 0.25, 0.15);
            serverWorld.playSound(null, getX(), getY(), getZ(),
                    SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.0F, 1.0F);
            SHATTER_HANDLER.accept(this);
            discard();
        }
    }
}
