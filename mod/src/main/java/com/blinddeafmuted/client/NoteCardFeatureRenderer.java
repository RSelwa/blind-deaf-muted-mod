package com.blinddeafmuted.client;

import com.blinddeafmuted.common.ModComponents;
import com.blinddeafmuted.common.ModConstants;
import com.blinddeafmuted.common.ModItems;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
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
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Draws a real 3D note card (a paper quad) held up in front of a player who has the
 * {@link ModItems#NOTE_CARD} in hand, with the written text drawn on its face — the
 * "square of paper with text like a sign" look.
 *
 * <p><b>Sea-of-Thieves inversion.</b> The card face flips based on brandish state
 * ({@link CardBrandishState#isBrandishing}):
 * <ul>
 *   <li><b>Brandishing</b> — the face turns OUTWARD (toward viewers in front), so everyone
 *       else reads it. The writer's own private {@link NoteCardHud} hides.</li>
 *   <li><b>Not brandishing</b> — the face turns toward the writer; a viewer in front sees the
 *       blank back. Only the writer reads it (via {@link NoteCardHud}).</li>
 * </ul>
 *
 * <p>Attached to the {@code body} (not an arm) so the card presents cleanly at chest height
 * regardless of arm animation; {@code PlayerEntityModelMixin} raises the arms so it reads as
 * "held up". <b>All the geometry constants below need in-game visual calibration</b> — same as
 * the other accessories.
 */
public final class NoteCardFeatureRenderer
        extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {

    private static final Identifier TEXTURE = ModConstants.id("textures/item/note_card.png");

    // --- Card placement (px in model space; tune these in-game) -------------------
    private static final int CARD_W = 16;   // width  (1 block)
    private static final int CARD_H = 20;   // height (1.25 block)
    /** Down from the neck pivot to chest height. */
    private static final float CHEST_DOWN = 7.0F;
    /** Forward from the body (-Z is the direction the player faces). */
    private static final float FORWARD = -7.0F;

    // --- Text layout on the face --------------------------------------------------
    /** Padding inside the card edge before text starts (px). */
    private static final float TEXT_PAD = 1.6F;
    /** Shrinks font pixels down so a full {@link ModComponents#MAX_LINE_LENGTH}-char line fits
     *  the card width. ~ (CARD_W - 2*pad) / (MAX_LINE_LENGTH * 6px per glyph). */
    private static final float TEXT_SCALE = 0.095F;
    /** Just in front of the +Z face so the text isn't z-fighting the paper. */
    private static final float TEXT_Z = 0.55F;
    private static final int INK = 0xFF3A2E12;

    private final ModelPart card;

    public NoteCardFeatureRenderer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
        this.card = buildCardModel().createModel();
    }

    /** A thin flat paper quad centred on its origin, textured from note_card.png (16×16). */
    private static TexturedModelData buildCardModel() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();
        root.addChild("card",
                ModelPartBuilder.create().uv(0, 0)
                        .cuboid(-CARD_W / 2f, -CARD_H / 2f, -0.5F, CARD_W, CARD_H, 1.0F),
                ModelTransform.NONE);
        return TexturedModelData.of(data, 16, 16);
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                       PlayerEntityRenderState state, float limbAngle, float limbDistance) {
        if (ModItems.NOTE_CARD == null) return;
        if (!holdsCard(state.name)) return;

        boolean brandishing = CardBrandishState.isBrandishing(state.name);
        List<String> lines = cardLines(state.name);

        PlayerEntityModel model = getContextModel();
        matrices.push();
        model.body.rotate(matrices);            // follow the torso
        matrices.translate(0.0F, CHEST_DOWN, FORWARD);
        // Brandishing turns the FACE (+Z) outward (toward viewers in front, i.e. -Z world);
        // otherwise the face points back toward the writer and viewers see the blank back.
        if (brandishing) {
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F));
        }

        // The paper.
        card.render(matrices,
                vertexConsumers.getBuffer(RenderLayer.getEntitySolid(TEXTURE)),
                light, OverlayTexture.DEFAULT_UV);

        // The text, on the +Z face.
        if (!lines.isEmpty()) {
            drawText(matrices, vertexConsumers, light, lines);
        }
        matrices.pop();
    }

    private void drawText(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                          List<String> lines) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        matrices.push();
        // Top-left of the text area on the +Z face, then shrink font px to card px.
        matrices.translate(-CARD_W / 2f + TEXT_PAD, -CARD_H / 2f + TEXT_PAD, TEXT_Z);
        matrices.scale(TEXT_SCALE, TEXT_SCALE, TEXT_SCALE);
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        int lineStep = tr.fontHeight + 2;
        int y = 0;
        for (String line : lines) {
            tr.draw(line, 0.0F, y, INK, false, matrix, vertexConsumers,
                    TextRenderer.TextLayerType.NORMAL, 0, light);
            y += lineStep;
        }
        matrices.pop();
    }

    /** Whether the named player currently holds a note card in either hand. */
    public static boolean holdsCard(String name) {
        PlayerEntity player = playerByName(name);
        if (player == null) return false;
        return player.getMainHandStack().isOf(ModItems.NOTE_CARD)
                || player.getOffHandStack().isOf(ModItems.NOTE_CARD);
    }

    /** The lines written on the named player's held card ({@link ModComponents#CARD_TEXT}). */
    private static List<String> cardLines(String name) {
        PlayerEntity player = playerByName(name);
        if (player == null) return List.of();
        var stack = player.getMainHandStack().isOf(ModItems.NOTE_CARD)
                ? player.getMainHandStack() : player.getOffHandStack();
        List<String> lines = stack.get(ModComponents.CARD_TEXT);
        return lines == null ? List.of() : lines;
    }

    private static PlayerEntity playerByName(String name) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return null;
        return client.world.getPlayers().stream()
                .filter(p -> p.getName().getString().equals(name))
                .findFirst().orElse(null);
    }
}
