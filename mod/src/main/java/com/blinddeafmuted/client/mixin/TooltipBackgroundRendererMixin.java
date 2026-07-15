package com.blinddeafmuted.client.mixin;

import com.blinddeafmuted.client.UIBlurRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.TooltipBackgroundRenderer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TooltipBackgroundRenderer.class)
public class TooltipBackgroundRendererMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private static void onRenderBackground(DrawContext context, int x, int y, int width, int height, int z, Identifier texture, CallbackInfo ci) {
        UIBlurRenderer.tooltipX = x;
        UIBlurRenderer.tooltipY = y;
        UIBlurRenderer.tooltipWidth = width;
        UIBlurRenderer.tooltipHeight = height;
        UIBlurRenderer.hasTooltipThisFrame = true;
    }
}


