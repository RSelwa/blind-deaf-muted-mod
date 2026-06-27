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

/**
 * Head-attached role accessories (idea #2), all real 3D {@link ModelPart} geometry:
 * <ul>
 *   <li>BLIND → dark glasses (also gets a cane via {@link BlindCaneFeatureRenderer}).</li>
 *   <li>MUTED → a beige plaster in an X over the mouth.</li>
 *   <li>DEAF  → an orange headset: two ear cups + a dark band over the crown.</li>
 * </ul>
 *
 * <p>One renderer for all three: it follows the head's transform, then draws whichever
 * accessory matches the player's role (looked up from the roster, like the cane).
 * Base textures are flat colours — the geometry carries the shape; repaint the PNGs in
 * {@code assets/blind-deaf-muted/textures/entity/} to personalise.
 *
 * <p>Head model space: the head box is x[-4,4] y[-8,0] z[-4,4], face on -Z.
 */
public final class RoleHeadAccessoryFeatureRenderer
        extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {

    private static final Identifier GLASSES_TEX = ModConstants.id("textures/entity/glasses.png");
    private static final Identifier BANDAGE_TEX = ModConstants.id("textures/entity/bandage.png");
    // Orange fill for the headset ear cups (reuses the old hard-hat PNG). The band +
    // connectors use their own near-black PNG. Repaint either to restyle.
    private static final Identifier HEADSET_TEX = ModConstants.id("textures/entity/hard_hat.png");
    private static final Identifier BAND_TEX = ModConstants.id("textures/entity/headset_band.png");

    private final ModelPart glasses;
    private final ModelPart bandage;
    private final ModelPart headsetCups;
    private final ModelPart headsetBand;

    public RoleHeadAccessoryFeatureRenderer(
            FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
        this.glasses = buildGlasses().createModel();
        this.bandage = buildBandage().createModel();
        this.headsetCups = buildHeadsetCups().createModel();
        this.headsetBand = buildHeadsetBand().createModel();
    }

    /** Two dark lenses over the eyes + a bridge, sitting just in front of the face. */
    private static TexturedModelData buildGlasses() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();
        root.addChild("left_lens",
                ModelPartBuilder.create().uv(0, 0).cuboid(-3.5F, -4.5F, -5.0F, 3.0F, 2.0F, 1.0F),
                ModelTransform.NONE);
        root.addChild("right_lens",
                ModelPartBuilder.create().uv(0, 0).cuboid(0.5F, -4.5F, -5.0F, 3.0F, 2.0F, 1.0F),
                ModelTransform.NONE);
        root.addChild("bridge",
                ModelPartBuilder.create().uv(0, 0).cuboid(-0.5F, -4.0F, -4.8F, 1.0F, 1.0F, 1.0F),
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
                ModelTransform.of(0.0F, -1.5F, -4.6F, pitch, yaw, (float) Math.toRadians(20.0)));
        root.addChild("strip_b",
                ModelPartBuilder.create().uv(0, 0).cuboid(-2.5F, -0.5F, -0.3F, 5.0F, 1.0F, 0.6F),
                ModelTransform.of(0.0F, -1.5F, -4.6F, pitch, yaw, (float) Math.toRadians(-20.0)));
        return TexturedModelData.of(data, 16, 16);
    }

    /** The two orange ear cups, one hugging each side of the head (x = ±4 faces).
     *  Each cup is a chunky 2×5×5 box; drawn with the orange {@link #HEADSET_TEX}. */
    private static TexturedModelData buildHeadsetCups() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();
        // Head sides are at x=+4 (left) and x=-4 (right); cups sit just outside, centred
        // vertically (head mid ≈ y=-4) and roughly over the ear (z centred).
        root.addChild("left_cup",
                ModelPartBuilder.create().uv(0, 0).cuboid(4.0F, -6.5F, -2.5F, 2.0F, 5.0F, 5.0F),
                ModelTransform.NONE);
        root.addChild("right_cup",
                ModelPartBuilder.create().uv(0, 0).cuboid(-6.0F, -6.5F, -2.5F, 2.0F, 5.0F, 5.0F),
                ModelTransform.NONE);
        return TexturedModelData.of(data, 16, 16);
    }

    /** The dark band arcing over the crown plus two short connectors down to the cups.
     *  All one part (one texture, {@link #BAND_TEX}); spans x = ±6 (the cups' outer faces). */
    private static TexturedModelData buildHeadsetBand() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();
        // Head top is y=-8; the band sits just above it (y=-9 → -7.5) so it reads as
        // an over-the-head arc rather than lying flat on the scalp.
        root.addChild("band",
                ModelPartBuilder.create().uv(0, 0).cuboid(-6.0F, -9.0F, -1.5F, 12.0F, 1.5F, 3.0F),
                ModelTransform.NONE);
        // Two small connectors bridging the gap from the band bottom (y=-7.5) down to
        // each cup top (y=-6.5), one over each ear (x centred on the cups, ≈ ±5).
        root.addChild("left_connector",
                ModelPartBuilder.create().uv(0, 0).cuboid(4.5F, -7.5F, -1.0F, 1.0F, 1.0F, 2.0F),
                ModelTransform.NONE);
        root.addChild("right_connector",
                ModelPartBuilder.create().uv(0, 0).cuboid(-5.5F, -7.5F, -1.0F, 1.0F, 1.0F, 2.0F),
                ModelTransform.NONE);
        return TexturedModelData.of(data, 16, 16);
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                       PlayerEntityRenderState state, float limbAngle, float limbDistance) {
        if (!SkinVisibilityState.isEnabled()) return; // op toggled accessories off
        Role role = RosterState.roleOf(state.name);
        if (role == Role.NONE) return;

        matrices.push();
        getContextModel().head.rotate(matrices); // follow head pitch/yaw
        switch (role) {
            case BLIND -> draw(matrices, vertexConsumers, light, glasses, GLASSES_TEX);
            case MUTED -> draw(matrices, vertexConsumers, light, bandage, BANDAGE_TEX);
            case DEAF  -> {
                // Headset uses two textures (orange cups + dark band) → two draws.
                draw(matrices, vertexConsumers, light, headsetCups, HEADSET_TEX);
                draw(matrices, vertexConsumers, light, headsetBand, BAND_TEX);
            }
            default    -> { /* NONE handled above */ }
        }
        matrices.pop();
    }

    /** Draw one accessory part with the given texture under the current head transform. */
    private static void draw(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                             int light, ModelPart part, Identifier texture) {
        part.render(matrices,
                vertexConsumers.getBuffer(RenderLayer.getEntitySolid(texture)),
                light, OverlayTexture.DEFAULT_UV);
    }
}
