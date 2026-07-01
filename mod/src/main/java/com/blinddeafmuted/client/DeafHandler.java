package com.blinddeafmuted.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * DEAF tuning keybind (default {@code H}, "hearing"): cycles the muffle intensity
 * ({@link DeafMuffle}) live so you can find the right feel in-game — the deaf analogue
 * of {@code B} for blind modes. The muffle itself is applied in {@code SourceMixin} via
 * {@link DeafAudioFilter}; this only flips which preset that filter uses.
 */
public final class DeafHandler {
    private DeafHandler() {}

    private static final KeyBinding CYCLE_MUFFLE = new KeyBinding(
            "key.blind-deaf-muted.cycle_deaf_muffle",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            "key.categories.blind-deaf-muted");

    public static void register() {
        KeyBindingHelper.registerKeyBinding(CYCLE_MUFFLE);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (CYCLE_MUFFLE.wasPressed()) {
                DeafMuffle level = DeafState.cycle();
                if (client.player != null) {
                    client.player.sendMessage(
                            Text.translatable("hud.blind-deaf-muted.deaf_muffle", level.name()),
                            true); // action bar
                }
            }
        });
    }
}
