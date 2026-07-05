package com.blinddeafmuted.client;

import com.blinddeafmuted.common.CardWritePayload;
import com.blinddeafmuted.common.ModComponents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;

import java.util.ArrayList;
import java.util.List;

/**
 * The note-card writing screen: {@link ModComponents#MAX_LINES} single-line fields, like
 * editing a sign. Pre-filled from the card currently in {@code hand}; on close it sends the
 * whole text to the server ({@link CardWritePayload}), which writes it onto the held stack.
 *
 * <p>Client-only (a {@link Screen}); the authoritative write is server-side so the text
 * survives and syncs to the other clients that read a brandished card.
 */
public final class CardEditScreen extends Screen {

    private final Hand hand;
    private final List<TextFieldWidget> fields = new ArrayList<>();

    /** Card look: a beige paper panel behind the fields. */
    private static final int PAPER = 0xFFE8DCB5;
    private static final int PAPER_BORDER = 0xFFB4A06E;
    private static final int FIELD_W = 176;
    private static final int FIELD_H = 16;
    private static final int ROW_GAP = 22;

    public CardEditScreen(Hand hand) {
        super(Text.translatable("screen.blind-deaf-muted.card"));
        this.hand = hand;
    }

    @Override
    protected void init() {
        fields.clear();
        List<String> existing = readExistingLines();

        int x = this.width / 2 - FIELD_W / 2;
        int topY = this.height / 2 - (ModComponents.MAX_LINES * ROW_GAP) / 2;

        for (int i = 0; i < ModComponents.MAX_LINES; i++) {
            TextFieldWidget field = new TextFieldWidget(
                    this.textRenderer, x, topY + i * ROW_GAP, FIELD_W, FIELD_H, Text.empty());
            field.setMaxLength(ModComponents.MAX_LINE_LENGTH);
            field.setDrawsBackground(true);
            if (i < existing.size()) field.setText(existing.get(i));
            fields.add(field);
            addDrawableChild(field);
        }
        setInitialFocus(fields.get(0));

        int doneY = topY + ModComponents.MAX_LINES * ROW_GAP + 8;
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), b -> close())
                .dimensions(this.width / 2 - 100, doneY, 200, 20).build());
    }

    /** Read the lines already written on the held card (empty if none / no card). */
    private List<String> readExistingLines() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return List.of();
        var stack = client.player.getStackInHand(hand);
        List<String> lines = stack.get(ModComponents.CARD_TEXT);
        return lines == null ? List.of() : lines;
    }

    @Override
    public void close() {
        // Trim trailing empties so the card doesn't reserve blank rows, but keep interior blanks.
        List<String> lines = new ArrayList<>();
        for (TextFieldWidget field : fields) lines.add(field.getText());
        int last = lines.size();
        while (last > 0 && lines.get(last - 1).isBlank()) last--;
        List<String> trimmed = List.copyOf(lines.subList(0, last));

        if (ClientPlayNetworking.canSend(CardWritePayload.ID)) {
            ClientPlayNetworking.send(new CardWritePayload(hand, trimmed));
        }
        super.close();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        // Paper panel behind the fields.
        int pad = 12;
        int x0 = this.width / 2 - FIELD_W / 2 - pad;
        int y0 = this.height / 2 - (ModComponents.MAX_LINES * ROW_GAP) / 2 - pad - 10;
        int x1 = this.width / 2 + FIELD_W / 2 + pad;
        int y1 = this.height / 2 + (ModComponents.MAX_LINES * ROW_GAP) / 2 + pad + 4;
        context.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, PAPER_BORDER);
        context.fill(x0, y0, x1, y1, PAPER);

        // Title in dark ink on the paper.
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, y0 + 6, 0xFF3A2E12);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
