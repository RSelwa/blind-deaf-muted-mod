package com.blinddeafmuted.client.mixin;

import com.blinddeafmuted.client.ReliefNauseaController;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Feeds the relief-nausea wobble ({@link ReliefNauseaController}) into vanilla's nausea
 * distortion in {@link GameRenderer#renderWorld} — WITHOUT touching
 * {@code ClientPlayerEntity.nauseaIntensity}, so {@code InGameHud} (which draws the nether
 * PORTAL overlay whenever that field is up without a real NAUSEA effect) stays quiet.
 *
 * <p>Two hooks, both on renderWorld's single occurrence of their target (verified against
 * the yarn-mapped jar via javap):
 * <ul>
 *   <li>the {@code MathHelper.lerp(tickDelta, prevNauseaIntensity, nauseaIntensity)} that
 *       computes the wobble intensity — lifted to at least our ramped strength. Vanilla
 *       multiplies the result by the accessibility "Distortion Effects" scale² right after,
 *       so that option keeps working on us too;</li>
 *   <li>the {@code hasStatusEffect(NAUSEA)} that picks the wobble speed — forced true while
 *       our wobble runs, so we get nausea's gentle speed (divisor 7), not the nether
 *       portal's frantic one (20).</li>
 * </ul>
 */
@Mixin(GameRenderer.class)
public class GameRendererNauseaMixin {

    @WrapOperation(method = "renderWorld",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/util/math/MathHelper;lerp(FFF)F"))
    private float blinddeafmuted$liftNauseaIntensity(
            float tickDelta, float start, float end, Operation<Float> original) {
        float vanilla = original.call(tickDelta, start, end);
        if (!ReliefNauseaController.active()) return vanilla;
        return Math.max(vanilla, ReliefNauseaController.lerped(tickDelta));
    }

    @ModifyExpressionValue(method = "renderWorld",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/network/ClientPlayerEntity;hasStatusEffect(Lnet/minecraft/registry/entry/RegistryEntry;)Z"))
    private boolean blinddeafmuted$forceNauseaWobbleSpeed(boolean hasNausea) {
        return hasNausea || ReliefNauseaController.active();
    }
}
