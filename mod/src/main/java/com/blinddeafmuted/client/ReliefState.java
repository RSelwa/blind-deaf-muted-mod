package com.blinddeafmuted.client;

import com.blinddeafmuted.common.ModEffects;
import net.minecraft.client.MinecraftClient;

/**
 * Client view of whether the LOCAL player is under a Potion of Relief (disability
 * temporarily reduced). Relief is a real vanilla status effect ({@link ModEffects#RELIEF}),
 * which vanilla already syncs to the owning client — so this just reads the player's own
 * effect list (no custom packet). The vanilla HUD draws the icon + countdown; the
 * client-side disability effects (blind vision, deaf world sound) read
 * {@link #disabilityRemaining()} to scale themselves down; the server scales voice itself.
 *
 * <p>The reduction amount is the live {@code reliefReductionPercent} config knob (read from
 * {@link ClientConfigState}), so it isn't tied to the effect instance.
 */
public final class ReliefState {
    private ReliefState() {}

    /** Whether the LOCAL player is currently under a Potion of Relief. */
    public static boolean localActive() {
        var player = MinecraftClient.getInstance().player;
        return player != null && player.hasStatusEffect(ModEffects.RELIEF);
    }

    /**
     * Fraction of the disability that REMAINS for the local player right now: {@code 1.0}
     * normally, or {@code 1 - reliefReductionPercent} while relieved (e.g. 0.25 at the default
     * 75% reduction). Effects lerp toward "no disability" as this drops — {@code effective =
     * lerp(remaining, normalValue, fullDisabilityValue)}.
     */
    public static float disabilityRemaining() {
        if (!localActive()) return 1.0f;
        float reduction = ClientConfigState.get().reliefReductionPercent();
        return Math.max(0.0f, Math.min(1.0f, 1.0f - reduction));
    }
}
