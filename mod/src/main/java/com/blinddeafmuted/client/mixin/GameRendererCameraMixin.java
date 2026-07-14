package com.blinddeafmuted.client.mixin;

import com.blinddeafmuted.client.MyopiaController;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Whenever the perspective changes (e.g. F5), vanilla Minecraft calls onCameraEntitySet
 * to refresh shaders. This explicitly destroys the current post-processor.
 * We intercept this and tell MyopiaController to reapply our shader if needed.
 */
@Mixin(GameRenderer.class)
public class GameRendererCameraMixin {

    @Inject(method = "onCameraEntitySet", at = @At("RETURN"))
    private void blinddeafmuted$onCameraEntitySet(Entity entity, CallbackInfo ci) {
        MyopiaController.forceReapply();
    }
}
