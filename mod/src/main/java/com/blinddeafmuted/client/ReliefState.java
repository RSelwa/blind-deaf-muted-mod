package com.blinddeafmuted.client;

import net.minecraft.client.MinecraftClient;

import java.util.Set;

/**
 * Client mirror of who is under a Potion of Relief (disability temporarily reduced),
 * from the server's {@code ReliefPayload}. The client-side disability effects (blind
 * vision, deaf world sound) read {@link #disabilityRemaining()} to scale themselves down
 * while the LOCAL player is relieved; the server scales voice itself.
 *
 * <p>The reduction amount is the live {@code reliefReductionPercent} config knob (read from
 * {@link ClientConfigState}), so it isn't sent in the relief packet.
 */
public final class ReliefState {
    private ReliefState() {}

    private static volatile Set<String> relieved = Set.of();

    /** Mirror the server's set of players currently under relief. */
    public static void set(Set<String> names) {
        relieved = names;
    }

    /** Whether the LOCAL player is currently under a Potion of Relief. */
    public static boolean localActive() {
        var player = MinecraftClient.getInstance().player;
        return player != null && relieved.contains(player.getName().getString());
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
