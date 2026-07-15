package com.blinddeafmuted.client.mixin;

import com.blinddeafmuted.client.ClientConfigState;
import com.blinddeafmuted.client.RoleState;
import com.blinddeafmuted.client.UIBlurRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {

    @Shadow protected int x;
    @Shadow protected int y;
    @Shadow protected int backgroundWidth;
    @Shadow protected int backgroundHeight;

    /**
     * Clear the tooltip flag at the start of rendering
     */
    @Inject(method = "render", at = @At("HEAD"))
    private void blinddeafmuted$clearTooltipFlag(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        UIBlurRenderer.hasTooltipThisFrame = false;
    }

    /**
     * Blur the inventory content area AFTER everything has been drawn (items, slots,
     * tooltips). The blur is purely visual — clicks still reach the underlying slots
     * because hit-testing uses logical coordinates, not pixels.
     */
    @Inject(method = "render", at = @At("TAIL"))
    private void blinddeafmuted$obscureInventory(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!RoleState.blindEffectActive()) return;

        float strength = ClientConfigState.get().blindInventoryObscureOpacity();
        if (strength > 0) {
            // Blur just the inventory window area (with a small margin for item tooltips
            // that might peek out). This keeps the rest of the screen (title bar, etc.)
            // readable so the player knows what screen they're on.
            int margin = 8;
            UIBlurRenderer.blurRegion(context,
                    this.x - margin,
                    this.y - margin,
                    this.x + this.backgroundWidth + margin,
                    this.y + this.backgroundHeight + margin,
                    strength);
        }
    }

    /**
     * Blur the tooltip AFTER it has been fully drawn.
     */
    @Inject(method = "drawMouseoverTooltip", at = @At("TAIL"))
    private void blinddeafmuted$obscureTooltip(DrawContext context, int x, int y, CallbackInfo ci) {
        if (!RoleState.blindEffectActive()) return;

        float strength = ClientConfigState.get().blindInventoryObscureOpacity();
        if (strength > 0 && UIBlurRenderer.hasTooltipThisFrame) {
            int tMargin = 4;
            UIBlurRenderer.blurRegion(context,
                    UIBlurRenderer.tooltipX - tMargin,
                    UIBlurRenderer.tooltipY - tMargin,
                    UIBlurRenderer.tooltipX + UIBlurRenderer.tooltipWidth + tMargin,
                    UIBlurRenderer.tooltipY + UIBlurRenderer.tooltipHeight + tMargin,
                    strength);
        }
    }
}
