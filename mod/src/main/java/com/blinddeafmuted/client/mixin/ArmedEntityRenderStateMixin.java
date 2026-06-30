package com.blinddeafmuted.client.mixin;

import com.blinddeafmuted.client.RosterState;
import com.blinddeafmuted.common.ModItems;
import com.blinddeafmuted.common.Role;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.ArmedEntityRenderState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides the VANILLA held-item model of our cane in a BLIND player's hand, so the only
 * cane on screen is the 3D one drawn by {@code BlindCaneFeatureRenderer} (which always
 * sits in the left hand with the swept-forward arm pose).
 *
 * <p>Without this, holding the cane gives you two canes: vanilla renders the item model
 * in whichever hand actually holds it, AND our feature renderer draws its own cane on
 * the left arm. We clear the matching hand's {@code ItemRenderState} right after vanilla
 * fills it (and blank that arm's pose so the empty hand hangs naturally).
 *
 * <p>Only blind players are affected — a non-blind player holding the cane still sees the
 * normal held item. Role is looked up from the roster by name (same as the accessories).
 */
@Mixin(ArmedEntityRenderState.class)
public class ArmedEntityRenderStateMixin {

    @Inject(method = "updateRenderState", at = @At("TAIL"))
    private static void blinddeafmuted$hideHeldCane(LivingEntity entity, ArmedEntityRenderState state,
                                                    ItemModelManager itemModelManager, CallbackInfo ci) {
        if (ModItems.CANE == null) return;
        if (!(entity instanceof PlayerEntity)) return;
        if (RosterState.roleOf(entity.getName().getString()) != Role.BLIND) return;

        if (entity.getStackInArm(Arm.RIGHT).isOf(ModItems.CANE)) {
            state.rightHandItemState.clear();
            state.rightArmPose = BipedEntityModel.ArmPose.EMPTY;
        }
        if (entity.getStackInArm(Arm.LEFT).isOf(ModItems.CANE)) {
            state.leftHandItemState.clear();
            state.leftArmPose = BipedEntityModel.ArmPose.EMPTY;
        }
    }
}
