package com.monkeys.client;

import com.monkeys.common.ModConstants;
import com.monkeys.common.Role;
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
 * Head-attached role accessories (idea #2), all real 3D {@link ModelPart} geometry:
 * <ul>
 *   <li>BLIND → dark glasses (also gets a cane via {@link BlindCaneFeatureRenderer}).</li>
 *   <li>MUTED → a beige plaster in an X over the mouth.</li>
 *   <li>DEAF  → an orange construction hard-hat (dome + front brim).</li>
 * </ul>
 *
 * <p>One renderer for all three: it follows the head's transform, then draws whichever
 * accessory matches the player's role (looked up from the roster, like the cane).
 * Base textures are flat colours — the geometry carries the shape; repaint the PNGs in
 * {@code assets/monkeys/textures/entity/} to personalise.
 *
 * <p>Head model space: the head box is x[-4,4] y[-8,0] z[-4,4], face on -Z.
 */
public final class RoleHeadAccessoryFeatureRenderer
        extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {

    private static final Identifier GLASSES_TEX = ModConstants.id("textures/entity/glasses.png");
    private static final Identifier BANDAGE_TEX = ModConstants.id("textures/entity/bandage.png");
    private static final Identifier HARD_HAT_TEX = ModConstants.id("textures/entity/hard_hat.png");

    private final ModelPart glasses;
    private final ModelPart bandage;
    private final ModelPart hardHat;

    public RoleHeadAccessoryFeatureRenderer(
            FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
        this.glasses = buildGlasses().createModel();
        this.bandage = buildBandage().createModel();
        this.hardHat = buildHardHat().createModel();
    }

    /** Two dark lenses over the eyes + a bridge, sitting just in front of the face. */
    private static TexturedModelData buildGlasses() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();
        root.addChild("left_lens",
                ModelPartBuilder.create().uv(0, 0).cuboid(-3.5F, -5.5F, -5.0F, 3.0F, 2.0F, 1.0F),
                ModelTransform.NONE);
        root.addChild("right_lens",
                ModelPartBuilder.create().uv(0, 0).cuboid(0.5F, -5.5F, -5.0F, 3.0F, 2.0F, 1.0F),
                ModelTransform.NONE);
        root.addChild("bridge",
                ModelPartBuilder.create().uv(0, 0).cuboid(-0.5F, -5.0F, -4.8F, 1.0F, 1.0F, 1.0F),
                ModelTransform.NONE);
        return TexturedModelData.of(data, 16, 16);
    }

    /** Two thin strips crossed into an X over the mouth. */
    private static TexturedModelData buildBandage() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();
        float pitch = 0.0F, yaw = 0.0F;
        // Each strip is centred on the origin; the child transform pins it to the mouth
        // (front of the face) and rotates it ±45° around Z to form the X.
        root.addChild("strip_a",
                ModelPartBuilder.create().uv(0, 0).cuboid(-2.5F, -0.5F, -0.3F, 5.0F, 1.0F, 0.6F),
                ModelTransform.of(0.0F, -2.5F, -4.6F, pitch, yaw, (float) Math.toRadians(45.0)));
        root.addChild("strip_b",
                ModelPartBuilder.create().uv(0, 0).cuboid(-2.5F, -0.5F, -0.3F, 5.0F, 1.0F, 0.6F),
                ModelTransform.of(0.0F, -2.5F, -4.6F, pitch, yaw, (float) Math.toRadians(-45.0)));
        return TexturedModelData.of(data, 16, 16);
    }

    /** A dome cap over the top of the head plus a brim jutting forward. */
    private static TexturedModelData buildHardHat() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();
        root.addChild("dome",
                ModelPartBuilder.create().uv(0, 0).cuboid(-4.5F, -10.0F, -4.5F, 9.0F, 2.5F, 9.0F),
                ModelTransform.NONE);
        root.addChild("brim",
                ModelPartBuilder.create().uv(0, 0).cuboid(-4.5F, -8.0F, -7.5F, 9.0F, 1.0F, 3.0F),
                ModelTransform.NONE);
        return TexturedModelData.of(data, 16, 16);
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                       PlayerEntityRenderState state, float limbAngle, float limbDistance) {
        Role role = RosterState.roleOf(state.name);
        if (role == Role.NONE) return;

        ModelPart accessory;
        Identifier texture;
        switch (role) {
            case BLIND -> { accessory = glasses;  texture = GLASSES_TEX; }
            case MUTED -> { accessory = bandage;  texture = BANDAGE_TEX; }
            case DEAF  -> { accessory = hardHat;  texture = HARD_HAT_TEX; }
            default    -> { return; }
        }

        matrices.push();
        getContextModel().head.rotate(matrices); // follow head pitch/yaw
        accessory.render(matrices,
                vertexConsumers.getBuffer(RenderLayer.getEntitySolid(texture)),
                light, OverlayTexture.DEFAULT_UV);
        matrices.pop();
    }
}
