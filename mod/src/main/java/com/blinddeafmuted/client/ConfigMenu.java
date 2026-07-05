package com.blinddeafmuted.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Keybind (default {@code O}) that opens the live-tuning {@link ConfigScreen}. Only opens when
 * no other screen is up, so it can't fire mid-inventory/chat.
 */
public final class ConfigMenu {
    private ConfigMenu() {}

    private static final KeyBinding OPEN = new KeyBinding(
            "key.blind-deaf-muted.open_config",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            "key.categories.blind-deaf-muted");

    public static void register() {
        KeyBindingHelper.registerKeyBinding(OPEN);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new ConfigScreen());
                }
            }
        });
    }
}
