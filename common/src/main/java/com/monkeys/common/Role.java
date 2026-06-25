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

    /** Case-insensitive parse used by the server command; null if unknown. */
    public static Role fromString(String s) {
        if (s == null) return null;
        for (Role r : values()) {
            if (r.name().equalsIgnoreCase(s)) return r;
        }
        return null;
    }
}
