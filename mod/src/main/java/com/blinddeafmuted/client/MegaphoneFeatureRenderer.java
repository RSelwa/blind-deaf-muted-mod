package com.blinddeafmuted.client;

import com.blinddeafmuted.common.ModConstants;
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

/**
 * Draws a flared megaphone/bullhorn in front of a player's mouth while they're holding
 * the megaphone key (push-to-megaphone). Visible to everyone, so teammates can see who
 * is shouting through to a DEAF player.
 *
 * <p>Real 3D {@link ModelPart} geometry attached to the head (same pattern as
 * {@link RoleHeadAccessoryFeatureRenderer}): a narrow mouthpiece against the face that
 * steps out into a wide flare, pointing along -Z (the look direction). Per-player it
 * checks {@link MegaphoneState} (keyed by name, like the role accessories) and only
 * draws when that player is active.
 *
 * <p>Head model space: the head box is x[-4,4] y[-8,0] z[-4,4], face on -Z; the mouth
 * sits around y=-2.5, z=-4. Texture reuses the orange accessory PNG — repaint
 * {@code textures/entity/hard_hat.png} (or point this at a dedicated PNG) to restyle.
 */
public final class MegaphoneFeatureRenderer
        extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {

    private static final Identifier TEXTURE = ModConstants.id("textures/entity/hard_hat.png");

    private final ModelPart megaphone;

    public MegaphoneFeatureRenderer(
            FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
        this.megaphone = build().createModel();
    }

    /** Mouthpiece against the face stepping out to a wide flare, both centred on the mouth. */
    private static TexturedModelData build() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();
        // Narrow neck: sits on the lower face (mouth ≈ y=-2.5), from the face (z=-4) outward.
        root.addChild("neck",
                ModelPartBuilder.create().uv(0, 0).cuboid(-1.5F, -4.0F, -6.0F, 3.0F, 3.0F, 2.0F),
                ModelTransform.NONE);
        // Wide flared bell further out (-Z), giving the cone silhouette.
        root.addChild("bell",
                ModelPartBuilder.create().uv(0, 0).cuboid(-3.0F, -5.5F, -9.0F, 6.0F, 6.0F, 3.0F),
                ModelTransform.NONE);
        return TexturedModelData.of(data, 16, 16);
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                       PlayerEntityRenderState state, float limbAngle, float limbDistance) {
        if (!MegaphoneState.isActive(state.name)) return;

        matrices.push();
        getContextModel().head.rotate(matrices); // follow head pitch/yaw
        megaphone.render(matrices,
                vertexConsumers.getBuffer(RenderLayer.getEntitySolid(TEXTURE)),
                light, OverlayTexture.DEFAULT_UV);
        matrices.pop();
    }
}
