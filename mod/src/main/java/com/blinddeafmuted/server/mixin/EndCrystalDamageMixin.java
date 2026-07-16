package com.blinddeafmuted.server.mixin;

import com.blinddeafmuted.server.CrystalGate;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.server.world.ServerWorld;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Enforces the "blind arrow crystal" rule server-side: cancels End-Crystal damage from a
 * non-blind projectile while the rule is active (see {@link CrystalGate}). Non-projectile
 * damage is left alone, so a crystal can still be meleed. Runs on the logical server (this
 * mixin config carries no {@code environment}, so it loads on both physical sides).
 */
@Mixin(EndCrystalEntity.class)
public class EndCrystalDamageMixin {

    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void bdm$gateCrystalDamage(ServerWorld world, DamageSource source, float amount,
                                       CallbackInfoReturnable<Boolean> cir) {
        if (CrystalGate.blocks(world, source)) {
            cir.setReturnValue(false); // "no damage dealt" — crystal survives
        }
    }
}
