package com.blinddeafmuted.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.util.math.MathHelper;

/**
 * The Potion of Relief's downside for the BLIND player: while relieved, the restored
 * sight comes with vanilla NAUSEA's screen wobble — the VISUAL only. No
 * {@code StatusEffectInstance} (no HUD icon, no inventory entry, nothing extra for
 * milk to clear); when relief ends the wobble ramps back out.
 *
 * <p>This class only owns the RAMP (ticked here, read per-frame). The actual injection
 * into the renderer lives in {@code GameRendererNauseaMixin}, which lifts the wobble
 * intensity inside {@code GameRenderer.renderWorld} to at least {@link #lerped}.
 *
 * <p>Why not just write {@code ClientPlayerEntity.nauseaIntensity} (the shared field the
 * real effect feeds)? Because vanilla treats "intensity without the NAUSEA status effect"
 * as <em>standing in a nether portal</em>: {@code InGameHud} draws the purple portal
 * overlay and {@code GameRenderer} picks the portal wobble speed (divisor 20) instead of
 * nausea's (7). First version did exactly that and looked like a nether trip. Keeping our
 * strength out of the player field means {@code InGameHud} sees 0 (no overlay) and the
 * mixin forces the nausea-speed branch — the result is exactly the nausea LOOK.
 */
public final class ReliefNauseaController {
    private ReliefNauseaController() {}

    /** Peak wobble while relieved (1 = a real full-strength NAUSEA effect — the mixin
     *  feeds this through the same distortion-scale math vanilla uses). Lower to soften. */
    private static float getTarget() {
        return ClientConfigState.get().blindReliefNauseaStrength();
    }

    /** Per-tick ramp in/out (0.05 ≈ 1 s from none to full and back). */
    private static final float RAMP_PER_TICK = 0.05f;

    private static float strength = 0.0f;
    private static float prevStrength = 0.0f;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) {
                strength = 0.0f;
                prevStrength = 0.0f;
                return;
            }
            prevStrength = strength;
            // Gate on blindEffectActive() (not just the role) so the debug suppress key
            // that clears the blind visuals for screenshots clears the wobble too.
            boolean active = RoleState.blindEffectActive() && ReliefState.localActive();
            strength = active
                    ? Math.min(getTarget(), strength + RAMP_PER_TICK)
                    : Math.max(0.0f, strength - RAMP_PER_TICK);
        });
    }

    /** Whether the synthetic wobble is currently non-zero (mixin gate). */
    public static boolean active() {
        return strength > 0.0f || prevStrength > 0.0f;
    }

    /** Frame-smooth strength, lerped between the last two ticks like vanilla lerps
     *  {@code prevNauseaIntensity}/{@code nauseaIntensity}. */
    public static float lerped(float tickDelta) {
        return MathHelper.lerp(tickDelta, prevStrength, strength);
    }
}
