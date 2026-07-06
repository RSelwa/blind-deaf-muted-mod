package com.blinddeafmuted.client;

import java.util.Objects;

import com.blinddeafmuted.client.mixin.GameRendererAccessor;
import com.blinddeafmuted.common.ModConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
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

    /** SOFT myopia (cane held) → assets/blind-deaf-muted/post_effect/myopia.json. */
    private static final Identifier MYOPIA = Identifier.of(ModConstants.MOD_ID, "myopia");
    /** HARD myopia (no cane) → assets/blind-deaf-muted/post_effect/myopia_hard.json. */
    private static final Identifier MYOPIA_HARD = Identifier.of(ModConstants.MOD_ID, "myopia_hard");
    /** RELIEF myopia (Potion of Relief) → post_effect/myopia_relief.json — near-clear sight,
     *  overriding cane/no-cane while the relief effect is active. */
    private static final Identifier MYOPIA_RELIEF = Identifier.of(ModConstants.MOD_ID, "myopia_relief");

    /** Pipeline currently installed by us, or null if none. Reinstall on any change. */
    private static Identifier applied = null;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Which pipeline (if any) the current blind state wants this tick.
            Identifier want = null;
            if (RoleState.blindEffectActive()) {
                // A Potion of Relief trumps the cane step: near-clear sight for its duration.
                boolean relieved = ReliefState.localActive();
                switch (RoleState.effectiveBlindMode()) {
                    case MYOPIA -> want = relieved ? MYOPIA_RELIEF : MYOPIA;           // cane → soft
                    case MYOPIA_HARD -> want = relieved ? MYOPIA_RELIEF : MYOPIA_HARD; // no cane → harsh
                    default -> want = null;                 // fog/blackout: not our shader
                }
            }

            if (Objects.equals(want, applied)) return; // only act on transitions
            if (client.gameRenderer == null) return;    // retry next tick, leave `applied`
            applied = want;

            if (want != null) {
                // Switching soft<->hard just re-sets the processor to the new pipeline.
                ((GameRendererAccessor) client.gameRenderer).blinddeafmuted$setPostProcessor(want);
            } else {
                client.gameRenderer.clearPostProcessor();
            }
        });
    }
}
