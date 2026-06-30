package com.blinddeafmuted.client;

import com.blinddeafmuted.common.MegaphonePayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Push-to-megaphone: while the megaphone key (default {@code R}) is held, this client
 * tells the server (via {@link MegaphonePayload}) that the local player is megaphoning,
 * so the voice-chat plugin renders their voice loud + saturated for DEAF listeners and
 * every client draws the megaphone-at-the-mouth model ({@link MegaphoneFeatureRenderer}).
 *
 * <p>Modelled on Simple Voice Chat's own push-to-talk: voice itself stays on (no PTT);
 * this key only flips the megaphone overlay. We send a packet <em>only on the
 * down→up / up→down transition</em>, not every tick, so it's one packet per press and
 * one per release.
 */
public final class MegaphoneController {
    private MegaphoneController() {}

    private static final KeyBinding KEY = new KeyBinding(
            "key.blind-deaf-muted.megaphone",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "key.categories.blind-deaf-muted");

    /** Last state we told the server, so we only send on change. */
    private static boolean lastSent = false;

    public static void register() {
        KeyBindingHelper.registerKeyBinding(KEY);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // No connection → reset, so the first press after (re)connecting always sends.
            if (client.player == null || client.getNetworkHandler() == null) {
                lastSent = false;
                return;
            }
            boolean down = KEY.isPressed();
            if (down != lastSent) {
                lastSent = down;
                ClientPlayNetworking.send(new MegaphonePayload(down));
            }
        });
    }
}
