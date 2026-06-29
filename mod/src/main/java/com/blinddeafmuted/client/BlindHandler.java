package com.blinddeafmuted.client;

import com.blinddeafmuted.common.Role;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import org.lwjgl.glfw.GLFW;

/**
 * BLIND effect driver.
 *
 * <p>Two responsibilities:
 * <ol>
 *   <li><b>Mode toggle keybind</b> (default {@code B}) — cycles {@link BlindMode} so
 *       you can flip between the two looks live while testing.</li>
 *   <li><b>VANILLA mode</b> — re-applies Minecraft's Blindness status effect to the
 *       local player every tick while blind. (The {@code BLACKOUT_HUD} mode is drawn
 *       by {@code InGameHudMixin}; nothing to do here for it.)</li>
 * </ol>
 *
 * <p>The Blindness effect is applied <i>client-side only</i>, consistent with the
 * "effects live on the client" design. NOTE: on a multiplayer server the server owns
 * a player's real status effects, so if we ever apply server-side effects to this
 * player they could fight this client-side one — fine today, flagged in DEVELOPER.md.
 */
public final class BlindHandler {
    private BlindHandler() {}

    private static final KeyBinding TOGGLE_MODE = new KeyBinding(
            "key.blind-deaf-muted.toggle_blind_mode",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            "key.categories.blind-deaf-muted");

    /** DEBUG: suppress the local blind effect (see your own accessories). Default N. */
    private static final KeyBinding TOGGLE_EFFECT = new KeyBinding(
            "key.blind-deaf-muted.toggle_blind_effect",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            "key.categories.blind-deaf-muted");

    /** Did we apply the vanilla Blindness effect last tick? (so we can clear it cleanly). */
    private static boolean appliedVanilla = false;

    public static void register() {
        KeyBindingHelper.registerKeyBinding(TOGGLE_MODE);
        KeyBindingHelper.registerKeyBinding(TOGGLE_EFFECT);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (TOGGLE_MODE.wasPressed()) {
                RoleState.toggleBlindMode();
                if (client.player != null) {
                    client.player.sendMessage(
                            net.minecraft.text.Text.literal("Blind mode: " + RoleState.getBlindMode()),
                            true); // action-bar message
                }
            }

            while (TOGGLE_EFFECT.wasPressed()) {
                RoleState.toggleBlindEffect();
                if (client.player != null) {
                    client.player.sendMessage(
                            net.minecraft.text.Text.literal("Blind effect: "
                                    + (RoleState.isBlindEffectSuppressed() ? "OFF (debug)" : "ON")),
                            true); // action-bar message
                }
            }

            if (client.player == null) return;

            boolean wantVanilla = RoleState.blindEffectActive()
                    && RoleState.getBlindMode() == BlindMode.VANILLA;

            if (wantVanilla) {
                // Re-apply each tick with a short duration so it never lapses while blind.
                // ambient + no particles + no HUD icon = clean look.
                client.player.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.BLINDNESS, 40, 0, true, false, false));
                appliedVanilla = true;
            } else if (appliedVanilla) {
                // Left vanilla-blind (role changed or toggled to BLACKOUT_HUD): clear it.
                client.player.removeStatusEffect(StatusEffects.BLINDNESS);
                appliedVanilla = false;
            }
        });
    }
}
