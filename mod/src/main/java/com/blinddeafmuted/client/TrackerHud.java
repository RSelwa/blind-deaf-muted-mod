package com.blinddeafmuted.client;

import com.blinddeafmuted.common.Role;
import com.blinddeafmuted.common.TrackerPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Teammate tracker HUD: a small stack of lines just above the health/food bar, one
 * per teammate, each reading {@code Name  142b  ↗} — name, distance in blocks, and a
 * direction arrow relative to where the player is currently looking (↑ = dead ahead).
 *
 * <p>It's a co-op quality-of-life aid so players don't get hopelessly lost, so it's
 * <b>on by default</b> and toggleable with a keybind (default {@code K}).
 *
 * <p>Data comes from the server via {@link TrackerPayload} (a client can't see the
 * positions of far-away players on its own); {@link TrackerState} holds the latest.
 * Rendering is driven from {@code InGameHudMixin} at the end of HUD rendering.
 */
public final class TrackerHud {
    private TrackerHud() {}

    /** 8 compass arrows, indexed by 45° sectors clockwise from "ahead". */
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    /** Pixels above the bottom of the screen for the lowest tracker line (clears the
     *  hearts + armor rows). */
    private static final int BOTTOM_OFFSET = 55;
    private static final int LINE_HEIGHT = 10;

    private static final KeyBinding TOGGLE = new KeyBinding(
            "key.blind-deaf-muted.toggle_tracker",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "key.categories.blind-deaf-muted");

    public static void register() {
        KeyBindingHelper.registerKeyBinding(TOGGLE);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (TOGGLE.wasPressed()) {
                boolean on = TrackerState.toggle();
                if (client.player != null) {
                    client.player.sendMessage(
                            Text.translatable("hud.blind-deaf-muted.tracker", Text.translatable(
                                    on ? "state.blind-deaf-muted.on" : "state.blind-deaf-muted.off")),
                            true); // action bar
                }
            }
        });
    }

    /** Draw the tracker lines. Called from InGameHudMixin at the TAIL of HUD render. */
    public static void render(DrawContext context) {
        if (!TrackerState.isEnabled()) return;
        if (RoleState.is(Role.BLIND)) return; // a blind player can't see the HUD

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;

        List<TrackerPayload.Entry> entries = TrackerState.getEntries();
        if (entries.isEmpty()) return;

        TextRenderer font = mc.textRenderer;
        int centerX = context.getScaledWindowWidth() / 2;
        int y = context.getScaledWindowHeight() - BOTTOM_OFFSET;

        double px = mc.player.getX();
        double py = mc.player.getY();
        double pz = mc.player.getZ();
        float yaw = mc.player.getYaw();

        // All teammates on one horizontal row, separated by spaced bars.
        StringBuilder row = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) row.append("   |   ");
            row.append(format(entries.get(i), px, py, pz, yaw));
        }
        context.drawCenteredTextWithShadow(font, Text.literal(row.toString()),
                centerX, y, 0xFFFFFFFF);
    }

    private static String format(TrackerPayload.Entry e, double px, double py, double pz, float yaw) {
        double dx = e.x() - px;
        double dy = e.y() - py;
        double dz = e.z() - pz;

        // 3D distance (still counts the vertical gap), but we no longer show an
        // elevation marker — the heading arrow alone keeps the row clean.
        long blocks = Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));

        // Yaw (degrees) that would face the target, then how far that is from where
        // we're actually looking. atan2(-dx, dz) matches Minecraft's yaw convention
        // (0° = +Z / south, increasing clockwise).
        double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
        double rel = wrapDegrees(targetYaw - yaw);
        int sector = Math.floorMod((int) Math.round(rel / 45.0), 8);
        String arrow = ARROWS[sector];

        return e.name() + "  " + blocks + "b  " + arrow;
    }

    /** Normalize an angle to the range (-180, 180]. */
    private static double wrapDegrees(double deg) {
        deg %= 360.0;
        if (deg >= 180.0) deg -= 360.0;
        if (deg < -180.0) deg += 360.0;
        return deg;
    }
}
