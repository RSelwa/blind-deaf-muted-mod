package com.monkeys.common;

/**
 * The disability role a player is currently assigned.
 *
 * <p>This is the heart of the mod: the server decides a player's Role and pushes
 * it to that player's client (see {@link RolePayload}); the client mod then applies
 * the matching local effect.
 *
 * <ul>
 *   <li>{@link #NONE}  — no disability (admins, spectators, or before assignment).</li>
 *   <li>{@link #BLIND} — client renders a black screen overlay.</li>
 *   <li>{@link #DEAF}  — client mutes all audio locally.</li>
 *   <li>{@link #MUTED} — client blocks chat / voice output.</li>
 * </ul>
 */
public enum Role {
    NONE,
    BLIND,
    DEAF,
    MUTED;

    /**
     * The disabilities eligible for <em>random</em> assignment, in a stable index
     * order (1 = DEAF, 2 = BLIND, 3 = MUTED). {@link #NONE} is intentionally
     * excluded — it is the "cleared" state, not a disability.
     *
     * <p>This array is the single source of truth for the random pool: its length
     * is the "randomness length" the server uses, and adding a future disability
     * to the game is as simple as adding it here (and to the enum above). The
     * random-assignment command guarantees every entry here is handed out at least
     * once before any disability is duplicated.
     */
    public static final Role[] ASSIGNABLE = { DEAF, BLIND, MUTED };

    /** Case-insensitive parse used by the server command; null if unknown. */
    public static Role fromString(String s) {
        if (s == null) return null;
        for (Role r : values()) {
            if (r.name().equalsIgnoreCase(s)) return r;
        }
        return null;
    }
}
