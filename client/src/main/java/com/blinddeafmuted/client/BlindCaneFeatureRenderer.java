package com.blinddeafmuted.client;

import com.blinddeafmuted.common.ModConstants;
import com.blinddeafmuted.common.Role;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/**
 * Renders a 3D white cane in a BLIND player's left hand, visible to everyone (so
 * teammates can spot who's blind). This is the first of the role "accessories"
 * (idea #2); glasses / bandage / hard-hat will reuse the exact same mechanism,
 * attached to {@code head} instead of {@code leftArm}.
 *
 * <p>The cane is real geometry — a {@link ModelPart} cuboid (2×24×2 px, ~1.5 blocks
 * long) — not a flat texture, baked once in the constructor. Whether to draw it is
 * decided per-player from the roster ({@link RosterState#roleOf}), since a feature
 * renderer only gets the render state, not the entity.
 */
public final class BlindCaneFeatureRenderer
        extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {

    private static final Identifier TEXTURE = ModConstants.id("textures/entity/blind_cane.png");

    // --- Hand-attachment transform (tweak these to reposition the cane) ----------
    /** Down the left arm from the shoulder pivot to roughly the hand (~10px). */
    private static final float HAND_DOWN = 0.65F;
    /** Slight forward lean so it reads as a sweeping cane, not a stiff pole. */
    private static final float FORWARD_TILT_DEGREES = 12.0F;

    private final ModelPart cane;

    public BlindCaneFeatureRenderer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
        this.cane = buildCaneModel().createModel();
    }

    /**
     * A long thin cuboid hanging downward from its origin (the grip at the top).
     * 1×24×1 px ≈ 1.5 blocks; UV-mapped from (0,0) into a 16×32 texture — see
     * {@code assets/blind-deaf-muted/textures/entity/blind_cane.png}.
     */
    private static TexturedModelData buildCaneModel() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();
        root.addChild("cane",
                ModelPartBuilder.create().uv(0, 0)
                        .cuboid(-0.5F, 0.0F, -0.5F, 1.0F, 24.0F, 1.0F),
                ModelTransform.NONE);
        return TexturedModelData.of(data, 16, 32);
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                       PlayerEntityRenderState state, float limbAngle, float limbDistance) {
        if (RosterState.roleOf(state.name) != Role.BLIND) return;

        PlayerEntityModel model = getContextModel();
        matrices.push();
        // Follow the left arm's live transform (so the cane swings with the arm)…
        model.leftArm.rotate(matrices);
        // …drop to the hand and lean the cane forward a touch.
        matrices.translate(0.0F, HAND_DOWN, 0.0F);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(FORWARD_TILT_DEGREES));

        cane.render(matrices,
                vertexConsumers.getBuffer(RenderLayer.getEntitySolid(TEXTURE)),
                light, OverlayTexture.DEFAULT_UV);
        matrices.pop();
    }
}
