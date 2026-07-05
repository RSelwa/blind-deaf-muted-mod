package com.blinddeafmuted.client;

import com.blinddeafmuted.common.MegaphonePayload;
import com.blinddeafmuted.common.ModItems;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import org.lwjgl.glfw.GLFW;

/**
 * Megaphone activation: pressing the megaphone key (default {@code R}) while holding a
 * megaphone item asks the server to fire a timed burst — {@link MegaphonePayload} with
 * {@code active=true} = "activate now".
 *
 * <p>The megaphone is no longer push-to-talk: the server owns a fixed 5&nbsp;s burst + a
 * 2&nbsp;min per-player cooldown ({@code MegaphoneState}), so this only needs to send a single
 * request on the key press. The server replies with an action-bar message (burst started, or
 * how long the cooldown has left) and, while the burst is live, renders the speaker loud for
 * deaf listeners. We only send while a megaphone is actually in hand (the server re-checks).
 */
public final class MegaphoneController {
    private MegaphoneController() {}

    private static final KeyBinding KEY = new KeyBinding(
            "key.blind-deaf-muted.megaphone",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "key.categories.blind-deaf-muted");

    public static void register() {
        KeyBindingHelper.registerKeyBinding(KEY);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.getNetworkHandler() == null) return;
            // Discrete presses (queued), not a held state — one activation request per press.
            while (KEY.wasPressed()) {
                if (holdsMegaphone(client.player)) {
                    ClientPlayNetworking.send(new MegaphonePayload(true));
                }
            }
        });
    }

    /** Whether the local player holds a megaphone in either hand. */
    private static boolean holdsMegaphone(PlayerEntity player) {
        if (ModItems.MEGAPHONE == null) return false;
        return player.getMainHandStack().isOf(ModItems.MEGAPHONE)
                || player.getOffHandStack().isOf(ModItems.MEGAPHONE);
    }
}
