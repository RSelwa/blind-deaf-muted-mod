package com.monkeys.client;

import com.monkeys.common.Role;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

/**
 * BLIND effect: paint the whole screen black on top of everything.
 *
 * <p>Drawing our own opaque layer (rather than relying on the vanilla Blindness
 * fog) is what keeps this shader-safe and fully "blind" — shaders can't render
 * past a solid HUD fill.
 */
public final class BlindOverlay {
    private BlindOverlay() {}

    public static void register() {
        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            if (!RoleState.is(Role.BLIND)) return;

            int w = drawContext.getScaledWindowWidth();
            int h = drawContext.getScaledWindowHeight();
            // Solid opaque black over the entire screen.
            drawContext.fill(0, 0, w, h, 0xFF000000);

            // TODO: decide whether the player should still see their own hotbar/
            // hand, or be 100% black. Pure black = hardest mode.
        });
    }
}
