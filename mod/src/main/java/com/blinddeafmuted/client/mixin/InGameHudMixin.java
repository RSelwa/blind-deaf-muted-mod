package com.blinddeafmuted.client.mixin;

import com.blinddeafmuted.client.BlindMode;
import com.blinddeafmuted.client.ReliefState;
import com.blinddeafmuted.client.RoleState;
import com.blinddeafmuted.client.RouletteAnimation;
import com.blinddeafmuted.client.TrackerHud;
import com.blinddeafmuted.common.Role;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * BLIND / {@link BlindMode#BLACKOUT_HUD}: paint the whole screen black at the START
 * of HUD rendering.
 *
 * <p>The world has already been drawn by this point, but the vanilla HUD layers
 * (hotbar, health, hunger, hand, etc.) have NOT — they render after this injection.
 * So our black fill hides the environment while the HUD draws on top of it and stays
 * visible. An open screen (inventory) renders later still, so it's visible too.
 *
 * <p>This is shader-safe: it's a flat opaque quad on the GUI layer, after the world
 * pass — shaders can't render past it.
 *
 * <p>A second injection at the TAIL draws the teammate tracker on top of the finished
 * HUD ({@link TrackerHud}). It runs after the blackout draw above, but the tracker
 * gates itself off while blind, so the two never both show.
 */
@Mixin(InGameHud.class)
public class InGameHudMixin {

    @Inject(
            method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
            at = @At("HEAD"))
    private void blinddeafmuted$blackoutBeforeHud(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (!RoleState.blindEffectActive()) return;
        if (RoleState.effectiveBlindMode() != BlindMode.BLACKOUT_HUD) return;

        int w = context.getScaledWindowWidth();
        int h = context.getScaledWindowHeight();
        // A Potion of Relief fades the blackout toward transparent (rem=1 → fully black,
        // rem=0.25 at the default 75% → ~25% black), so the player sees mostly through it.
        int alpha = (int) (255 * ReliefState.disabilityRemaining());
        if (alpha <= 0) return;
        context.fill(0, 0, w, h, alpha << 24);
    }

    @Inject(
            method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
            at = @At("TAIL"))
    private void blinddeafmuted$drawTracker(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        TrackerHud.render(context);
        // Roster HUD replaced by the vanilla scoreboard sidebar (server/RosterScoreboard).
        // NoteCardHud disabled: the owner's "your note" rectangle was unwanted. The writer
        // reads their note by opening the editor (G); the world card is for showing others.
        RouletteAnimation.render(context); // last = on top of everything
    }
}
