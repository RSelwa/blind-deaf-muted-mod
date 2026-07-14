package com.blinddeafmuted.client;

import com.blinddeafmuted.common.CardWritePayload;
import com.blinddeafmuted.common.ModComponents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The note-card writing screen — drawn on the actual vanilla book background
 * ({@code textures/gui/book.png}), with dark ink and <b>no text shadow</b> and a blinking
 * cursor, exactly like the vanilla book-and-quill. We roll our own text input (rather than
 * {@code TextFieldWidget}) so the ink has no shadow and the page stays clean; per-line editing
 * keeps it sign-like ({@link ModComponents#MAX_LINES} lines × {@link ModComponents#MAX_LINE_LENGTH}
 * chars).
 *
 * <p>Pre-filled from the card in {@code hand}; on close it sends the whole text to the server
 * ({@link CardWritePayload}), which writes it onto the held stack so it survives + syncs to the
 * clients that read a brandished card.
 */
public final class CardEditScreen extends Screen {

    /** The vanilla book GUI image (book on the left page area). 192×192 inside a 256×256 sheet. */
    private static final Identifier BOOK_TEXTURE = Identifier.ofVanilla("textures/gui/book.png");
    private static final int BOOK_W = 192;
    private static final int BOOK_H = 192;
    /** Text inset inside the book page (matches vanilla book-and-quill). */
    private static final int TEXT_INSET_X = 36;
    private static final int TEXT_INSET_Y = 32;
    private static final int LINE_H = 12;
    private static final int INK = 0xFF3A2E12;   // dark ink, no shadow
    private static final int CURSOR = 0xFF3A2E12;
    /** Translucent blue behind each line while everything is selected (Ctrl+A). */
    private static final int SELECTION = 0x8033B3FF;

    /** Quick pre-made messages, one button each in a column left of the book. Clicking
     *  REPLACES the whole note with the (localized) label. Lang keys
     *  {@code card.blind-deaf-muted.preset.<key>} (en+fr). */
    private static final String[] PRESETS = {"yes", "no", "help", "danger", "follow", "wait", "come", "run"};
    private static final int PRESET_W = 80;
    private static final int PRESET_H = 20;
    private static final int PRESET_GAP = 2;

    private final Hand hand;

    /** Editable text, one StringBuilder per line. */
    private final List<StringBuilder> lines = new ArrayList<>();
    private int cursorLine = 0;
    private int cursorCol = 0;
    /** Frame counter for the blinking cursor (advanced in {@link #tick()}). */
    private int blink = 0;
    /** Ctrl+A "everything is selected" state — the next backspace/delete/char clears the card. */
    private boolean allSelected = false;

    public CardEditScreen(Hand hand) {
        super(Text.translatable("screen.blind-deaf-muted.card"));
        this.hand = hand;
    }

    @Override
    protected void init() {
        if (lines.isEmpty()) {
            List<String> existing = readExistingLines();
            for (int i = 0; i < ModComponents.MAX_LINES; i++) {
                lines.add(new StringBuilder(i < existing.size() ? existing.get(i) : ""));
            }
            cursorLine = 0;
            cursorCol = lines.get(0).length();
        }

        int doneY = bookTop() + BOOK_H - 36;
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), b -> close())
                .dimensions(this.width / 2 - 98, doneY, 196, 20).build());

        // Pre-made message column, left of the book page.
        int px = Math.max(4, bookLeft() - PRESET_W - 8);
        int py = bookTop() + 8;
        for (String key : PRESETS) {
            Text label = Text.translatable("card.blind-deaf-muted.preset." + key);
            addDrawableChild(ButtonWidget.builder(label, b -> applyPreset(label.getString()))
                    .dimensions(px, py, PRESET_W, PRESET_H).build());
            py += PRESET_H + PRESET_GAP;
        }
    }

    /** Replace the whole note with one preset message (line 1, rest blank). */
    private void applyPreset(String text) {
        for (StringBuilder sb : lines) sb.setLength(0);
        String clipped = text.length() > ModComponents.MAX_LINE_LENGTH
                ? text.substring(0, ModComponents.MAX_LINE_LENGTH) : text;
        lines.get(0).append(clipped);
        cursorLine = 0;
        cursorCol = clipped.length();
        allSelected = false;
    }

    private int bookLeft() {
        return (this.width - BOOK_W) / 2;
    }

    private int bookTop() {
        return (this.height - BOOK_H) / 2;
    }

    private List<String> readExistingLines() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return List.of();
        List<String> l = client.player.getStackInHand(hand).get(ModComponents.CARD_TEXT);
        return l == null ? List.of() : l;
    }

    // --- Input ------------------------------------------------------------------

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (chr < ' ') return super.charTyped(chr, modifiers);
        if (allSelected) clearAll(); // typing over a full selection replaces it
        StringBuilder line = lines.get(cursorLine);
        if (line.length() >= ModComponents.MAX_LINE_LENGTH
                && cursorLine == ModComponents.MAX_LINES - 1) {
            return true; // last line full — nowhere to wrap, drop the char
        }
        line.insert(cursorCol, chr);
        cursorCol++;
        wrapOverflow();
        return true;
    }

    /**
     * Word-wraps any line longer than {@link ModComponents#MAX_LINE_LENGTH} onto the following
     * line (cascading down), moving the cursor with the text it sits in — so typing past the
     * end of a line flows onto the next one instead of blocking. Overflow past the last line
     * is dropped.
     */
    private void wrapOverflow() {
        for (int i = 0; i < lines.size(); i++) {
            StringBuilder line = lines.get(i);
            if (line.length() <= ModComponents.MAX_LINE_LENGTH) continue;
            // Break at the last space that keeps the head within the limit; hard-cut if none.
            int cut = line.lastIndexOf(" ", ModComponents.MAX_LINE_LENGTH);
            int keep = cut > 0 ? cut : ModComponents.MAX_LINE_LENGTH;
            int tailStart = cut > 0 ? cut + 1 : ModComponents.MAX_LINE_LENGTH; // the space itself is dropped
            String tail = line.substring(tailStart);
            line.setLength(keep);
            if (i == lines.size() - 1) {
                if (cursorLine == i) cursorCol = Math.min(cursorCol, keep);
                break;
            }
            StringBuilder next = lines.get(i + 1);
            if (!tail.isEmpty()) {
                next.insert(0, next.length() > 0 ? tail + " " : tail);
            }
            if (cursorLine == i && cursorCol > keep) {
                cursorLine = i + 1;
                cursorCol = cursorCol - tailStart;
            }
        }
    }

    /** Wipe the whole card and drop the selection (Ctrl+A → backspace/type). */
    private void clearAll() {
        for (StringBuilder sb : lines) sb.setLength(0);
        cursorLine = 0;
        cursorCol = 0;
        allSelected = false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (isSelectAll(keyCode)) {
            allSelected = true;
            return true;
        }
        if (allSelected) {
            allSelected = false;
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_DELETE) {
                clearAll();
                return true;
            }
        }
        StringBuilder line = lines.get(cursorLine);
        switch (keyCode) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (cursorCol > 0) {
                    line.deleteCharAt(cursorCol - 1);
                    cursorCol--;
                } else if (cursorLine > 0) {
                    cursorLine--;
                    cursorCol = lines.get(cursorLine).length();
                }
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (cursorCol < line.length()) line.deleteCharAt(cursorCol);
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_DOWN -> {
                if (cursorLine < ModComponents.MAX_LINES - 1) {
                    cursorLine++;
                    cursorCol = Math.min(cursorCol, lines.get(cursorLine).length());
                }
                return true;
            }
            case GLFW.GLFW_KEY_UP -> {
                if (cursorLine > 0) {
                    cursorLine--;
                    cursorCol = Math.min(cursorCol, lines.get(cursorLine).length());
                }
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                if (cursorCol > 0) cursorCol--;
                else if (cursorLine > 0) { cursorLine--; cursorCol = lines.get(cursorLine).length(); }
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                if (cursorCol < line.length()) cursorCol++;
                else if (cursorLine < ModComponents.MAX_LINES - 1) { cursorLine++; cursorCol = 0; }
                return true;
            }
            default -> { return super.keyPressed(keyCode, scanCode, modifiers); }
        }
    }

    @Override
    public void tick() {
        blink++;
    }

    @Override
    public void close() {
        List<String> out = new ArrayList<>();
        for (StringBuilder sb : lines) out.add(sb.toString());
        int last = out.size();
        while (last > 0 && out.get(last - 1).isBlank()) last--;
        List<String> trimmed = List.copyOf(out.subList(0, last));

        if (ClientPlayNetworking.canSend(CardWritePayload.ID)) {
            ClientPlayNetworking.send(new CardWritePayload(hand, trimmed));
        }
        super.close();
    }

    // --- Render -----------------------------------------------------------------

    /** NO dim and NO menu blur — the vanilla book-and-quill draws straight over the
     *  world, so we match it (any fill here reads as a black overlay the real book
     *  doesn't have). */
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        int bx = bookLeft();
        int by = bookTop();
        // The real vanilla book page.
        context.drawTexture(RenderLayer::getGuiTextured, BOOK_TEXTURE,
                bx, by, 0.0F, 0.0F, BOOK_W, BOOK_H, 256, 256);

        int tx = bx + TEXT_INSET_X;
        int ty = by + TEXT_INSET_Y;
        boolean showCursor = (blink / 6) % 2 == 0;
        for (int i = 0; i < lines.size(); i++) {
            int ly = ty + i * LINE_H;
            String text = lines.get(i).toString();
            if (allSelected && !text.isEmpty()) {
                int w = this.textRenderer.getWidth(text);
                context.fill(tx - 1, ly - 1, tx + w + 1, ly + this.textRenderer.fontHeight, SELECTION);
            }
            // Dark ink, NO shadow (last arg false).
            context.drawText(this.textRenderer, text, tx, ly, INK, false);
            if (i == cursorLine && showCursor && !allSelected) {
                int cx = tx + this.textRenderer.getWidth(text.substring(0, cursorCol));
                context.fill(cx, ly - 1, cx + 1, ly + this.textRenderer.fontHeight, CURSOR);
            }
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
