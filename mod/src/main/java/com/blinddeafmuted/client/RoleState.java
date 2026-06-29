package com.blinddeafmuted.client;

import com.blinddeafmuted.common.Role;

/**
 * Holds the local player's current {@link Role}.
 *
 * <p>One tiny shared place the effect handlers read every frame/tick. Set from the
 * network thread via {@code client.execute(...)}, so it's only written on the
 * client thread — a plain volatile field is enough.
 */
public final class RoleState {
    private RoleState() {}

    private static volatile Role current = Role.NONE;

    /**
     * How the BLIND role is rendered. Purely a client-side visual style of the same
     * disability (the player can't see the environment either way), so it's safe to
     * let the player pick it — see {@link BlindMode}. Default: {@link BlindMode#VANILLA}
     * (the tight closing-in fog where you only see your feet; toggle to BLACKOUT with B).
     */
    private static volatile BlindMode blindMode = BlindMode.VANILLA;

    /**
     * DEBUG/TEST flag: when true, the local BLIND visual effect (vanilla Blindness +
     * fog + blackout HUD) is suppressed even though the role is still BLIND. Lets you
     * see your own blind accessories (cane/glasses) for screenshots without going dark.
     * Does NOT change the role on the server or in the roster, so the accessories stay
     * on. Toggle with the keybind wired in {@link BlindHandler} (default N).
     */
    private static volatile boolean blindEffectSuppressed = false;

    public static Role get() {
        return current;
    }

    static void set(Role role) {
        current = role;
    }

    public static boolean is(Role role) {
        return current == role;
    }

    /**
     * True when the BLIND vision effect should actually apply: role is BLIND and the
     * debug suppress flag is off. The three effect sites (vanilla Blindness in
     * {@link BlindHandler}, fog in {@code BackgroundRendererMixin}, blackout in
     * {@code InGameHudMixin}) gate on this instead of {@code is(Role.BLIND)}.
     */
    public static boolean blindEffectActive() {
        return current == Role.BLIND && !blindEffectSuppressed;
    }

    public static boolean isBlindEffectSuppressed() {
        return blindEffectSuppressed;
    }

    /** Toggle the debug blind-effect suppression (wired to a keybind for testing). */
    public static void toggleBlindEffect() {
        blindEffectSuppressed = !blindEffectSuppressed;
    }

    public static BlindMode getBlindMode() {
        return blindMode;
    }

    /** Cycle to the next blind mode (wired to a keybind for live testing). */
    public static void toggleBlindMode() {
        BlindMode[] all = BlindMode.values();
        blindMode = all[(blindMode.ordinal() + 1) % all.length];
    }
}
