package com.blinddeafmuted.client;

import com.blinddeafmuted.common.ModComponents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.List;

/**
 * The writer's PRIVATE read of their own note card (Sea-of-Thieves inversion): a paper
 * panel drawn on the HUD while the local player holds a card and is NOT brandishing it.
 * The moment they brandish (right-click), this hides and the card face turns outward for
 * everyone else ({@link NoteCardFeatureRenderer}) — so you can either read it yourself OR
 * show it, never both.
 *
 * <p>Drawn from {@code InGameHudMixin} at the HUD TAIL.
 */
public final class NoteCardHud {
    private NoteCardHud() {}

    private static final int PAPER = 0xF2E8DCB5;
    private static final int PAPER_BORDER = 0xFFB4A06E;
    private static final int INK = 0xFF3A2E12;
    private static final int PAD = 6;

    public static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        // Only while holding a card and reading it privately (not showing others).
        if (NoteCardController.holdingHand(client.player) == null) return;
        if (CardBrandishState.localActive()) return;

        var hand = NoteCardController.holdingHand(client.player);
        var stack = client.player.getStackInHand(hand);
        List<String> lines = stack.get(ModComponents.CARD_TEXT);

        var tr = client.textRenderer;
        // Header hint + the written lines (or a "write it" prompt when empty).
        Text header = Text.translatable("hud.blind-deaf-muted.card_reading");
        List<Text> body = new java.util.ArrayList<>();
        if (lines == null || lines.isEmpty()) {
            body.add(Text.translatable("hud.blind-deaf-muted.card_empty"));
        } else {
            for (String line : lines) body.add(Text.literal(line));
        }

        int contentW = tr.getWidth(header);
        for (Text t : body) contentW = Math.max(contentW, tr.getWidth(t));
        int lineStep = tr.fontHeight + 2;
        int contentH = lineStep /*header*/ + 3 + body.size() * lineStep;

        // Bottom-centre, above the hotbar.
        int panelW = contentW + PAD * 2;
        int panelH = contentH + PAD * 2;
        int x0 = (context.getScaledWindowWidth() - panelW) / 2;
        int y0 = context.getScaledWindowHeight() - panelH - 44;

        context.fill(x0 - 1, y0 - 1, x0 + panelW + 1, y0 + panelH + 1, PAPER_BORDER);
        context.fill(x0, y0, x0 + panelW, y0 + panelH, PAPER);

        int tx = x0 + PAD;
        int ty = y0 + PAD;
        context.drawText(tr, header, tx, ty, INK, false);
        ty += lineStep + 3;
        for (Text t : body) {
            context.drawText(tr, t, tx, ty, INK, false);
            ty += lineStep;
        }
    }
}
