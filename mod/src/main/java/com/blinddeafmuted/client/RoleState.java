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

    public static Role get() {
        return current;
    }

    static void set(Role role) {
        current = role;
    }

    public static boolean is(Role role) {
        return current == role;
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
