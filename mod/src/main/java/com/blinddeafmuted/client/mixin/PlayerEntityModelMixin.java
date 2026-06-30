package com.blinddeafmuted.client.mixin;

import com.blinddeafmuted.client.BlindCaneFeatureRenderer;
import com.blinddeafmuted.client.MegaphoneState;
import com.blinddeafmuted.client.RosterState;
import com.blinddeafmuted.common.Role;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.util.Arm;
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

    /** Switch for the megaphone right-arm raise. Disabled for now — the pose mis-bent
     *  the arm. Flip to {@code true} to bring it back. (The blind left-arm pose stays on.) */
    private static final boolean MEGAPHONE_ARM_POSE_ENABLED = false;

    /** Left-arm pitch while blind. ~-69°: forward and angled down, like a held cane.
     *  0 = hanging down, -π/2 ≈ -1.57 = straight forward. Tweak to taste. */
    private static final float BLIND_LEFT_ARM_PITCH = -1.2F;

    /** Right-arm pose while megaphoning, raising the hand up to the mouth as if holding
     *  the bullhorn (which {@code MegaphoneFeatureRenderer} draws at the mouth).
     *  Pitch raises the arm up-and-forward (0 = down, -π ≈ straight up); roll tilts the
     *  hand inward toward the face centre. Tweak to taste. */
    private static final float MEGAPHONE_ARM_PITCH = -2.3F;
    private static final float MEGAPHONE_ARM_ROLL = -0.5F;

    @Inject(
            method = "setAngles(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)V",
            at = @At("TAIL"))
    private void blinddeafmuted$extendBlindArm(PlayerEntityRenderState state, CallbackInfo ci) {
        if (RosterState.roleOf(state.name) != Role.BLIND) return;
        // Only sweep the arm forward when the cane is actually in hand, and sweep the
        // SAME arm that grips it (matches the cane accessory, which renders in that hand).
        Arm arm = BlindCaneFeatureRenderer.caneArm(state.name);
        if (arm == null) return;

        PlayerEntityModel model = (PlayerEntityModel) (Object) this;
        if (arm == Arm.RIGHT) {
            model.rightArm.pitch = BLIND_LEFT_ARM_PITCH;
            model.rightArm.yaw = 0.0F;
            model.rightArm.roll = 0.0F;
            // Keep the jacket sleeve overlay aligned with the arm we just moved.
            model.rightSleeve.pitch = model.rightArm.pitch;
            model.rightSleeve.yaw = 0.0F;
            model.rightSleeve.roll = 0.0F;
        } else {
            model.leftArm.pitch = BLIND_LEFT_ARM_PITCH;
            model.leftArm.yaw = 0.0F;
            model.leftArm.roll = 0.0F;
            model.leftSleeve.pitch = model.leftArm.pitch;
            model.leftSleeve.yaw = 0.0F;
            model.leftSleeve.roll = 0.0F;
        }
    }

    /** Raise the RIGHT arm to the mouth while megaphoning, so the player reads as holding
     *  the bullhorn up to their face. Separate from the blind pose (left arm) so a player
     *  who is both blind and megaphoning gets both. */
    @Inject(
            method = "setAngles(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)V",
            at = @At("TAIL"))
    private void blinddeafmuted$raiseMegaphoneArm(PlayerEntityRenderState state, CallbackInfo ci) {
        if (!MEGAPHONE_ARM_POSE_ENABLED) return;
        if (!MegaphoneState.isActive(state.name)) return;

        PlayerEntityModel model = (PlayerEntityModel) (Object) this;
        model.rightArm.pitch = MEGAPHONE_ARM_PITCH;
        model.rightArm.yaw = 0.0F;
        model.rightArm.roll = MEGAPHONE_ARM_ROLL;
        // Keep the jacket sleeve overlay aligned with the arm we just moved.
        model.rightSleeve.pitch = model.rightArm.pitch;
        model.rightSleeve.yaw = 0.0F;
        model.rightSleeve.roll = model.rightArm.roll;
    }
}
