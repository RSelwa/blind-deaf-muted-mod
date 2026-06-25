package com.monkeys.client;

import com.monkeys.common.Role;

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

    public static Role get() {
        return current;
    }

    static void set(Role role) {
        current = role;
    }

    public static boolean is(Role role) {
        return current == role;
    }
}
