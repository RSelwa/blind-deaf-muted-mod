package com.monkeys.client.mixin;

import com.monkeys.client.RosterState;
import com.monkeys.common.Role;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Holds a BLIND player's LEFT arm out in front (instead of hanging at their side), so
 * they read as sweeping their cane forward. Purely client-side cosmetic: it overrides
 * the arm rotation AFTER vanilla has posed the model.
 *
 * <p>The cane ({@code BlindCaneFeatureRenderer}) follows the left arm's transform, so
 * pinning the arm forward swings the cane forward too — no change needed there.
 *
 * <p>The role is looked up from the roster by name (same as the accessories), so every
 * client poses each blind player's arm identically.
 */
@Mixin(PlayerEntityModel.class)
public class PlayerEntityModelMixin {

    /** Left-arm pitch while blind. ~-69°: forward and angled down, like a held cane.
     *  0 = hanging down, -π/2 ≈ -1.57 = straight forward. Tweak to taste. */
    private static final float BLIND_LEFT_ARM_PITCH = -1.2F;

    @Inject(
            method = "setAngles(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)V",
            at = @At("TAIL"))
    private void monkeys$extendBlindArm(PlayerEntityRenderState state, CallbackInfo ci) {
        if (RosterState.roleOf(state.name) != Role.BLIND) return;

        PlayerEntityModel model = (PlayerEntityModel) (Object) this;
        model.leftArm.pitch = BLIND_LEFT_ARM_PITCH;
        model.leftArm.yaw = 0.0F;
        model.leftArm.roll = 0.0F;
        // Keep the jacket sleeve overlay aligned with the arm we just moved.
        model.leftSleeve.pitch = model.leftArm.pitch;
        model.leftSleeve.yaw = 0.0F;
        model.leftSleeve.roll = 0.0F;
    }
}
