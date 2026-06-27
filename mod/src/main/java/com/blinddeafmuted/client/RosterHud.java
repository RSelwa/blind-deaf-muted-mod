package com.blinddeafmuted.client;

import com.blinddeafmuted.common.RosterPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Leaderboard HUD: the "who is what" roster, drawn as a small right-aligned stack in
 * the top-right corner. Each row reads {@code Name  Blind}, with the role word in its
 * role colour (red = blind, gold = deaf, light-purple = muted, gray = none). The
 * local player's own name is bold so you can spot yourself at a glance.
 *
 * <p>It mirrors {@link TrackerHud}: data arrives from the server via
 * {@link RosterPayload} (held in {@link RosterState}), it's on by default, and it's
 * toggleable with a keybind (default {@code L}). Rendering is driven from
 * {@code InGameHudMixin} at the TAIL of HUD rendering.
 */
public final class RosterHud {
    private RosterHud() {}

    /** Pixels of inset from the top and right edges of the screen. */
    private static final int MARGIN = 4;
    private static final int LINE_HEIGHT = 10;

    private static final KeyBinding TOGGLE = new KeyBinding(
            "key.blind-deaf-muted.toggle_roster",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_L,
            "key.categories.blind-deaf-muted");

    public static void register() {
        KeyBindingHelper.registerKeyBinding(TOGGLE);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (TOGGLE.wasPressed()) {
                boolean on = RosterState.toggle();
                if (client.player != null) {
                    client.player.sendMessage(
                            Text.literal("Roster: " + (on ? "ON" : "OFF")),
                            true); // action bar
                }
            }
        });
    }

    /** Draw the roster. Called from InGameHudMixin at the TAIL of HUD render. */
    public static void render(DrawContext context) {
        if (!RosterState.isEnabled()) return;
        // NOTE: intentionally NOT hidden while blind. The roster is out-of-character
        // "who is what" meta info, useful even to a blind player. It's drawn at the
        // HUD TAIL, so in blackout mode it renders on top of the black fill (readable
        // white-on-black) rather than being hidden by it.

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;

        List<RosterPayload.Entry> entries = RosterState.getEntries();
        if (entries.isEmpty()) return;

        TextRenderer font = mc.textRenderer;
        int rightX = context.getScaledWindowWidth() - MARGIN;
        String selfName = mc.player.getGameProfile().getName();

        // Header, then one line per player, all right-aligned to the screen edge.
        Text header = Text.literal("— Blind Deaf Muted —").formatted(Formatting.YELLOW, Formatting.BOLD);
        drawRightAligned(context, font, header, rightX, MARGIN);

        for (int i = 0; i < entries.size(); i++) {
            RosterPayload.Entry e = entries.get(i);
            int y = MARGIN + (i + 1) * LINE_HEIGHT;
            drawRightAligned(context, font, formatRow(e, selfName), rightX, y);
        }
    }

    /** Build one row: {@code Name  Role}, name white (bold if it's you), role coloured. */
    private static Text formatRow(RosterPayload.Entry e, String selfName) {
        boolean self = e.name().equals(selfName);
        MutableText name = Text.literal(e.name())
                .formatted(self ? Formatting.WHITE : Formatting.GRAY);
        if (self) name.formatted(Formatting.BOLD);

        return name
                .append(Text.literal("  ").formatted(Formatting.WHITE))
                .append(Text.literal(e.role().label()).formatted(e.role().color()));
    }

    /** Draw {@code text} so its right edge sits at {@code rightX}. */
    private static void drawRightAligned(DrawContext context, TextRenderer font,
                                         Text text, int rightX, int y) {
        int x = rightX - font.getWidth(text);
        context.drawTextWithShadow(font, text, x, y, 0xFFFFFFFF);
    }
}
