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
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Draws a real 3D note card (a paper quad) held up in front of a player who is
 * BRANDISHING their {@link ModItems#NOTE_CARD} (right-click toggle), with the written
 * text on the outward face — Sea-of-Thieves "show the map" style.
 *
 * <p><b>Sea-of-Thieves inversion, as actually rendered:</b>
 * <ul>
 *   <li><b>Not brandishing</b> — this renderer draws NOTHING. The card shows as a normal
 *       held item (vanilla model), and only the writer reads the text via their private
 *       {@link NoteCardHud}.</li>
 *   <li><b>Brandishing</b> — the vanilla held item is hidden
 *       ({@code ArmedEntityRenderStateMixin}), both arms raise
 *       ({@code PlayerEntityModelMixin}), and this renderer draws the paper panel at chest
 *       height, text facing OUTWARD (-Z, the player's front) so viewers in front read it.
 *       The writer's private HUD hides.</li>
 * </ul>
 *
 * <p><b>Units.</b> Feature-renderer matrices are in BLOCK units, while ModelPart cuboids
 * are authored in pixels (1 px = 1/16 block) — every hand-made translate below multiplies
 * by {@link #PX}. (The first version forgot this and translated by whole blocks: the card
 * ended up metres away and the text 16× too big.)
 *
 * <p>Attached to the {@code body} part so the card follows torso tilt (sneaking) and
 * presents cleanly regardless of arm animation.
 */
public final class NoteCardFeatureRenderer
        extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {

    /** Dedicated entity texture (32×32) — the 12×16×1 cuboid needs a 26×18 UV area,
     *  which overflows the 16×16 item icon the first version pointed at. */
    private static final Identifier TEXTURE = ModConstants.id("textures/entity/note_card.png");

    /** One model pixel in block units. */
    private static final float PX = 1.0F / 16.0F;

    // --- Card placement (px in model space, relative to the body pivot = neck) --------
    private static final int CARD_W = 12;   // width  (0.75 block)
    private static final int CARD_H = 16;   // height (1 block)
    /** Card centre below the neck pivot (+Y is down in model space). */
    private static final float CARD_CENTER_DOWN = 3.5F;
    /** Card centre in front of the body (model front is -Z). Matches where the raised
     *  hands land ({@code PlayerEntityModelMixin.NOTE_CARD_ARM_PITCH}). */
    private static final float CARD_FORWARD = 9.0F;

    // --- Text layout on the front (-Z) face -------------------------------------------
    /** Padding inside the card edge before text starts (px). */
    private static final float TEXT_PAD = 1.2F;
    /** Air gap between the paper face and the glyphs (px) so text never z-fights. */
    private static final float TEXT_GAP = 0.3F;
    /** Text auto-fits the card: the scale grows until the widest line fills the card
     *  width or all lines fill its height, capped here (block-units-per-font-pixel).
     *  Short messages render BIG; a worst-case 22-char × 6-line note still fits
     *  (shrinks to ≈ 0.0045). */
    private static final float MAX_TEXT_SCALE = 0.02F;
    private static final int INK = 0xFF3A2E12;

    private final ModelPart card;

    public NoteCardFeatureRenderer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
        this.card = buildCardModel().createModel();
    }

    /** A thin flat paper quad centred on its origin, textured from note_card.png (32×32). */
    private static TexturedModelData buildCardModel() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();
        root.addChild("card",
                ModelPartBuilder.create().uv(0, 0)
                        .cuboid(-CARD_W / 2f, -CARD_H / 2f, -0.5F, CARD_W, CARD_H, 1.0F),
                ModelTransform.NONE);
        return TexturedModelData.of(data, 32, 32);
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                       PlayerEntityRenderState state, float limbAngle, float limbDistance) {
        if (ModItems.NOTE_CARD == null) return;
        // Only while SHOWING the card. Not brandishing → vanilla held item, nothing here.
        if (!CardBrandishState.isBrandishing(state.name)) return;
        if (!holdsCard(state.name)) return;

        List<String> lines = cardLines(state.name);

        matrices.push();
        // Follow the torso (so the card tilts with sneaking), then hold the panel out in
        // front of the chest. The panel is vertical, faces along ±Z; the player's front
        // is -Z, so the text face points at viewers standing in front.
        getContextModel().body.rotate(matrices);
        matrices.translate(0.0F, CARD_CENTER_DOWN * PX, -CARD_FORWARD * PX);

        // The paper + text render FULLBRIGHT (like glowing sign text) so the note stays
        // readable at night / in caves — the whole point is communicating.
        int fullbright = LightmapTextureManager.MAX_LIGHT_COORDINATE;
        card.render(matrices,
                vertexConsumers.getBuffer(RenderLayer.getEntitySolid(TEXTURE)),
                fullbright, OverlayTexture.DEFAULT_UV);

        // The text, floated just off the front (-Z) face. In model space (the whole
        // entity is rendered through a scale(-1,-1,1) flip) glyph +x/+y project to a
        // front viewer's screen-right/screen-down, so plain positive scale reads
        // correctly — no mirror compensation needed.
        if (!lines.isEmpty()) {
            drawText(matrices, vertexConsumers, fullbright, lines);
        }
        matrices.pop();
    }

    private void drawText(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                          List<String> lines) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int lineStep = tr.fontHeight + 2;

        // Auto-fit: grow the font until the widest line fills the card width or the
        // lines fill its height, capped at MAX_TEXT_SCALE. Short notes read BIG.
        int widest = 1;
        for (String line : lines) widest = Math.max(widest, tr.getWidth(line));
        float availW = (CARD_W - 2 * TEXT_PAD) * PX;
        float availH = (CARD_H - 2 * TEXT_PAD) * PX;
        float scale = Math.min(MAX_TEXT_SCALE,
                Math.min(availW / widest, availH / (lines.size() * lineStep)));

        matrices.push();
        // Card centre on the -Z face; lines centred like a sign.
        matrices.translate(0.0F, 0.0F, -(0.5F + TEXT_GAP) * PX);
        matrices.scale(scale, scale, scale);
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        float y = -(lines.size() * lineStep) / 2f;
        for (String line : lines) {
            tr.draw(line, -tr.getWidth(line) / 2f, y, INK, false, matrix, vertexConsumers,
                    TextRenderer.TextLayerType.NORMAL, 0, light);
            y += lineStep;
        }
        matrices.pop();
    }

    /** Whether the named player currently holds a note card in either hand. */
    public static boolean holdsCard(String name) {
        return cardArm(name) != null;
    }

    /** Which physical arm the named player holds the note card in, or {@code null} if neither. */
    public static Arm cardArm(String name) {
        if (ModItems.NOTE_CARD == null) return null;
        PlayerEntity player = playerByName(name);
        if (player == null) return null;
        if (player.getStackInArm(Arm.RIGHT).isOf(ModItems.NOTE_CARD)) return Arm.RIGHT;
        if (player.getStackInArm(Arm.LEFT).isOf(ModItems.NOTE_CARD)) return Arm.LEFT;
        return null;
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
