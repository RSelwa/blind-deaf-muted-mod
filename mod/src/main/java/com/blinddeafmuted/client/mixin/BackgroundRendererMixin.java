package com.blinddeafmuted.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.blinddeafmuted.client.BlindMode;
import com.blinddeafmuted.client.RoleState;
import com.blinddeafmuted.common.Role;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Fog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Tightens the fog while a player is BLIND in a fog mode: very tight (~2 blocks,
 * "see only your feet") for {@code FOG_HARD}, looser (~7 blocks) for {@code FOG_MEDIUM}
 * (the cane-eased level).
 *
 * <p>Vanilla Blindness already shrinks the view, but only to a few blocks. We want our
 * own control, so we rewrite the {@link Fog} that {@link BackgroundRenderer#applyFog}
 * returns, keeping its colour/shape but forcing the start/end for the current level.
 *
 * <p>Gated on our own blind state + mode, so fog from any other source (or the
 * BLACKOUT_HUD / MYOPIA blind styles, which don't use fog at all) is untouched.
 */
@Mixin(BackgroundRenderer.class)
public class BackgroundRendererMixin {

    /** Fog start while blind (both levels start at the camera). */
    private static final float BLIND_FOG_START = 0.0F;
    /** HARD fog end: fully fogged by ~2 blocks — you see only your feet. */
    private static final float BLIND_FOG_HARD_END = 2.0F;
    /** MEDIUM fog end (cane held): looser, ~7 blocks — you make out your surroundings. */
    private static final float BLIND_FOG_MEDIUM_END = 7.0F;

    @ModifyReturnValue(method = "applyFog", at = @At("RETURN"))
    private static Fog blinddeafmuted$tightenBlindFog(Fog fog) {
        if (!RoleState.blindEffectActive()) return fog;
        BlindMode mode = RoleState.effectiveBlindMode();
        float end;
        if (mode == BlindMode.FOG_HARD) {
            end = BLIND_FOG_HARD_END;
        } else if (mode == BlindMode.FOG_MEDIUM) {
            end = BLIND_FOG_MEDIUM_END;
        } else {
            return fog; // BLACKOUT_HUD / MYOPIA don't use fog
        }
        return new Fog(BLIND_FOG_START, end, fog.shape(),
                fog.red(), fog.green(), fog.blue(), fog.alpha());
    }
}
