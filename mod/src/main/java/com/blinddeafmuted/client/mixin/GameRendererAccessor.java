package com.blinddeafmuted.client.mixin;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes {@link GameRenderer}'s private {@code setPostProcessor(Identifier)} so the
 * MYOPIA blind look can install our custom {@code blind-deaf-muted:myopia} post-effect
 * shader. ({@code clearPostProcessor()} is already public, so we only need a setter.)
 *
 * <p>Once set, the vanilla render loop drives the post effect every frame from its id —
 * we never touch the FrameGraph ourselves.
 */
@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    @Invoker("setPostProcessor")
    void blinddeafmuted$setPostProcessor(Identifier id);
}
