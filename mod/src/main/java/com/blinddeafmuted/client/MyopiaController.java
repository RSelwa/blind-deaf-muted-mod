package com.blinddeafmuted.client;

import com.blinddeafmuted.client.mixin.GameRendererAccessor;
import com.blinddeafmuted.common.ModConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

/**
 * BLIND / {@link BlindMode#MYOPIA}: install the custom depth-aware blur post-effect
 * shader while the local player is blind in MYOPIA mode, and tear it down when they
 * leave it.
 *
 * <p>The shader ({@code assets/blind-deaf-muted/post_effect/myopia.json}) keeps the
 * few blocks right in front of the camera sharp and smears everything beyond into
 * unreadable shapes — you can tell "something's there" but not which block. Tune the
 * blur falloff in {@code shaders/post/myopia.fsh} (SHARP_DEPTH / FULL_BLUR_DEPTH /
 * MAX_TEXEL_RADIUS).
 *
 * <p>We drive it through {@link net.minecraft.client.render.GameRenderer}'s post-processor
 * slot (the same mechanism the spectator-mob shaders use): set our id while active,
 * clear it when done. The render loop applies it automatically each frame.
 */
public final class MyopiaController {
    private MyopiaController() {}

    /** Our post-effect pipeline id → assets/blind-deaf-muted/post_effect/myopia.json. */
    private static final Identifier MYOPIA = Identifier.of(ModConstants.MOD_ID, "myopia");

    /** Did we install our post processor last tick? (so we only clear what we set). */
    private static boolean applied = false;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean want = RoleState.blindEffectActive()
                    && RoleState.effectiveBlindMode() == BlindMode.MYOPIA;

            if (want == applied) return; // only act on transitions
            applied = want;

            if (client.gameRenderer == null) return;
            if (want) {
                ((GameRendererAccessor) client.gameRenderer).blinddeafmuted$setPostProcessor(MYOPIA);
            } else {
                client.gameRenderer.clearPostProcessor();
            }
        });
    }
}
