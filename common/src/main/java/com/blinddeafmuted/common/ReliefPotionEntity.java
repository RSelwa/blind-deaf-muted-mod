package com.blinddeafmuted.common;

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
 * The thrown "Potion of Relief" — a splash-potion-style projectile that, when it shatters,
 * temporarily reduces the disability of every player within range (the co-op boost for the
 * Ender Dragon fight). Modelled on {@link RandomizerBottleEntity}.
 *
 * <p>The shatter <em>effect</em> (applying relief) is server-only game logic in the
 * {@code server} module, which {@code common} can't reference, so the server installs its
 * behaviour into {@link #SHATTER_HANDLER} at init; this entity just invokes it on impact.
 */
public class ReliefPotionEntity extends ThrownItemEntity {

    /**
     * Server-installed callback invoked (server-side only) when a bottle shatters.
     * Defaults to a no-op; {@code BlindDeafMutedServer} replaces it with the relief logic.
     */
    public static Consumer<ReliefPotionEntity> SHATTER_HANDLER = entity -> {};

    public ReliefPotionEntity(EntityType<? extends ReliefPotionEntity> type, World world) {
        super(type, world);
    }

    public ReliefPotionEntity(World world, LivingEntity owner, ItemStack stack) {
        super(ModEntities.RELIEF_POTION_BOTTLE, owner, world, stack);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.RELIEF_POTION;
    }

    /** A gentle arc like a splash potion. */
    @Override
    protected double getGravity() {
        return 0.05;
    }

    @Override
    protected void onCollision(HitResult hitResult) {
        super.onCollision(hitResult);
        if (getWorld() instanceof ServerWorld serverWorld) {
            // Positive-vibe green sparkle burst + the usual glass break.
            serverWorld.spawnParticles(ParticleTypes.HAPPY_VILLAGER,
                    getX(), getY() + 0.2, getZ(), 40, 0.4, 0.4, 0.4, 0.2);
            serverWorld.spawnParticles(ParticleTypes.INSTANT_EFFECT,
                    getX(), getY() + 0.2, getZ(), 20, 0.3, 0.3, 0.3, 0.1);
            serverWorld.playSound(null, getX(), getY(), getZ(),
                    SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.0F, 1.0F);
            SHATTER_HANDLER.accept(this);
            discard();
        }
    }
}
