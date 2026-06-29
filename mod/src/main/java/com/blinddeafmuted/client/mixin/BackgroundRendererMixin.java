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
 * Tightens the fog to "you can only see your feet" while a player is BLIND in the
 * VANILLA blind mode.
 *
 * <p>Vanilla Blindness already shrinks the view, but only to a few blocks. We want it
 * much tighter, so we rewrite the {@link Fog} that {@link BackgroundRenderer#applyFog}
 * returns, keeping its colour/shape but forcing a very short start/end.
 *
 * <p>Gated on our own blind state + mode, so fog from any other source (or the
 * BLACKOUT_HUD blind style, which doesn't use fog at all) is untouched.
 */
@Mixin(BackgroundRenderer.class)
public class BackgroundRendererMixin {

    /** Fog start/end in blocks while blind: 0 → fully fogged by ~2 blocks. Tweak here. */
    private static final float BLIND_FOG_START = 0.0F;
    private static final float BLIND_FOG_END = 2.0F;

    @ModifyReturnValue(method = "applyFog", at = @At("RETURN"))
    private static Fog blinddeafmuted$tightenBlindFog(Fog fog) {
        if (RoleState.blindEffectActive() && RoleState.getBlindMode() == BlindMode.VANILLA) {
            return new Fog(BLIND_FOG_START, BLIND_FOG_END, fog.shape(),
                    fog.red(), fog.green(), fog.blue(), fog.alpha());
        }
        return fog;
    }
}
