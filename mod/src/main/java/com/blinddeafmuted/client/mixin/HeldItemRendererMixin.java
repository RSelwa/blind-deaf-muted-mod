package com.blinddeafmuted.client.mixin;

import com.blinddeafmuted.client.CardBrandishState;
import com.blinddeafmuted.common.ModConstants;
import com.blinddeafmuted.common.ModItems;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * First-person view of BRANDISHING your own note card: both player arms raised holding the
 * card up, with just the TOP of the card peeking into view from the bottom of the screen —
 * so the writer always KNOWS they're showing the board (they can't read it: the text face
 * points away, at the audience — the Sea-of-Thieves inversion).
 *
 * <p>Modeled on vanilla's {@code renderMapInBothHands} (the filled-map two-hand hold is
 * exactly this pose): same sway/equip/pitch handling, same {@code renderArm} calls, but the
 * card panel replaces the map — sunk low so it doesn't block the view.
 *
 * <p>While brandishing, BOTH first-person hand passes are cancelled (the card is a two-hand
 * hold; whatever the other hand carries is out of frame).
 */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {

    @Shadow
    protected abstract void renderArm(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                      int light, Arm arm);

    @Unique
    private static final Identifier BDM_CARD_TEXTURE = ModConstants.id("textures/entity/note_card.png");

    /** How far the card sinks below the two-hand anchor (post-2×-scale units) — bigger =
     *  less card on screen. Tuned so roughly the top quarter peeks into view. */
    @Unique
    private static final float BDM_CARD_SINK = 0.6F;

    /** Same 12×16×1 px paper quad as {@code NoteCardFeatureRenderer}, same 32×32 texture. */
    @Unique
    private static final ModelPart BDM_CARD = bdm$buildCard();

    private static ModelPart bdm$buildCard() {
        ModelData data = new ModelData();
        data.getRoot().addChild("card",
                ModelPartBuilder.create().uv(0, 0).cuboid(-6.0F, -8.0F, -0.5F, 12.0F, 16.0F, 1.0F),
                ModelTransform.NONE);
        return TexturedModelData.of(data, 32, 32).createModel();
    }

    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"), cancellable = true)
    private void blinddeafmuted$renderBrandishedCard(AbstractClientPlayerEntity player, float tickDelta,
                                                     float pitch, Hand hand, float swingProgress,
                                                     ItemStack item, float equipProgress,
                                                     MatrixStack matrices,
                                                     VertexConsumerProvider vertexConsumers, int light,
                                                     CallbackInfo ci) {
        if (ModItems.NOTE_CARD == null || !CardBrandishState.localActive()) return;
        boolean mainHasCard = player.getMainHandStack().isOf(ModItems.NOTE_CARD);
        boolean offHasCard = player.getOffHandStack().isOf(ModItems.NOTE_CARD);
        if (!mainHasCard && !offHasCard) return;

        // Draw the two-hand hold once, on the pass whose hand actually has the card
        // (main hand wins when both do); the other hand's pass is just cancelled.
        boolean cardPass = item.isOf(ModItems.NOTE_CARD) && (hand == Hand.MAIN_HAND || !mainHasCard);
        if (cardPass) {
            bdm$renderCardInBothHands(player, pitch, swingProgress, equipProgress,
                    matrices, vertexConsumers, light);
        }
        ci.cancel();
    }

    @Unique
    private void bdm$renderCardInBothHands(AbstractClientPlayerEntity player, float pitch,
                                           float swingProgress, float equipProgress,
                                           MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                           int light) {
        matrices.push();
        // Sway + equip slide + pitch-follow, copied from vanilla renderMapInBothHands.
        float root = MathHelper.sqrt(swingProgress);
        float swayY = -0.2F * MathHelper.sin(swingProgress * (float) Math.PI);
        float swayZ = -0.4F * MathHelper.sin(root * (float) Math.PI);
        matrices.translate(0.0F, -swayY / 2.0F, swayZ);
        float angle = bdm$cardAngle(pitch);
        matrices.translate(0.0F, 0.04F + equipProgress * -1.2F + angle * -0.5F, -0.72F);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(angle * -85.0F));

        if (!player.isInvisible()) {
            matrices.push();
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90.0F));
            this.renderArm(matrices, vertexConsumers, light, Arm.RIGHT);
            this.renderArm(matrices, vertexConsumers, light, Arm.LEFT);
            matrices.pop();
        }

        float swing = MathHelper.sin(root * (float) Math.PI);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(swing * 20.0F));
        matrices.scale(2.0F, 2.0F, 2.0F);

        // The panel: BACK toward the camera (you're showing it, not reading it), upright
        // (ModelPart space is y-down, the Z-180 flips it), sunk so only the top peeks in.
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180.0F));
        matrices.translate(0.0F, BDM_CARD_SINK, 0.0F);
        BDM_CARD.render(matrices,
                vertexConsumers.getBuffer(RenderLayer.getEntitySolid(BDM_CARD_TEXTURE)),
                LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
        matrices.pop();
    }

    /** Vanilla's map raise curve: looking down raises the held thing into view. */
    @Unique
    private static float bdm$cardAngle(float pitch) {
        float f = 1.0F - pitch / 45.0F + 0.1F;
        f = MathHelper.clamp(f, 0.0F, 1.0F);
        return -MathHelper.cos(f * (float) Math.PI) * 0.5F + 0.5F;
    }
}
