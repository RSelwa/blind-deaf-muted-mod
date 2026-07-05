package com.blinddeafmuted.client.mixin;

import com.blinddeafmuted.common.ModItems;
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
 * Hides the VANILLA held-item model of our cane in ANY holder's hand, so the only cane
 * on screen is the 3D one drawn by {@code BlindCaneFeatureRenderer} in the holding hand
 * with the swept-forward arm pose.
 *
 * <p>Without this, holding the cane gives you two canes: vanilla renders the item model
 * in whichever hand actually holds it, AND our feature renderer draws its own cane on
 * the same arm. We clear the matching hand's {@code ItemRenderState} right after vanilla
 * fills it (and blank that arm's pose so vanilla doesn't re-bend the arm).
 *
 * <p>Applies to any player holding the cane (matches the arm-pose mixin and the feature
 * renderer), so the cane looks identical whether the holder is blind or not.
 */
@Mixin(ArmedEntityRenderState.class)
public class ArmedEntityRenderStateMixin {

    @Inject(method = "updateRenderState", at = @At("TAIL"))
    private static void blinddeafmuted$hideHeldCane(LivingEntity entity, ArmedEntityRenderState state,
                                                    ItemModelManager itemModelManager, CallbackInfo ci) {
        if (ModItems.CANE == null) return;
        if (!(entity instanceof PlayerEntity)) return;

        if (entity.getStackInArm(Arm.RIGHT).isOf(ModItems.CANE)) {
            state.rightHandItemState.clear();
            state.rightArmPose = BipedEntityModel.ArmPose.EMPTY;
        }
        if (entity.getStackInArm(Arm.LEFT).isOf(ModItems.CANE)) {
            state.leftHandItemState.clear();
            state.leftArmPose = BipedEntityModel.ArmPose.EMPTY;
        }
    }

    /**
     * Hide the VANILLA held-item model of the note card WHILE ITS HOLDER IS BRANDISHING it,
     * so the only card on screen during a show is the big 3D one drawn by
     * {@code NoteCardFeatureRenderer} (held up at the chest, both hands raised by
     * {@code PlayerEntityModelMixin}). When not brandishing, the vanilla item stays visible —
     * the card just sits in the hand like any item (Sea-of-Thieves: map lowered vs held up).
     */
    @Inject(method = "updateRenderState", at = @At("TAIL"))
    private static void blinddeafmuted$hideHeldNoteCard(LivingEntity entity, ArmedEntityRenderState state,
                                                        ItemModelManager itemModelManager, CallbackInfo ci) {
        if (ModItems.NOTE_CARD == null) return;
        if (!(entity instanceof PlayerEntity)) return;
        if (!com.blinddeafmuted.client.CardBrandishState.isBrandishing(entity.getName().getString())) return;

        if (entity.getStackInArm(Arm.RIGHT).isOf(ModItems.NOTE_CARD)) {
            state.rightHandItemState.clear();
            state.rightArmPose = BipedEntityModel.ArmPose.EMPTY;
        }
        if (entity.getStackInArm(Arm.LEFT).isOf(ModItems.NOTE_CARD)) {
            state.leftHandItemState.clear();
            state.leftArmPose = BipedEntityModel.ArmPose.EMPTY;
        }
    }
}
