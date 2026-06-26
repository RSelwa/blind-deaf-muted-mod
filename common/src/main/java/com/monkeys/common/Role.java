package com.monkeys.common;

import net.minecraft.util.Formatting;

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
 *
 * <p>Each role also carries a {@link Formatting color} and a human-readable
 * {@link #label() label}, used for the "You're now …" join message, the
 * roster/leaderboard HUD, and the re-roll animation. Keeping them on the enum
 * makes it the single source of truth for both the game logic and the UI.
 */
public enum Role {
    NONE("None", Formatting.GRAY),
    BLIND("Blind", Formatting.RED),
    DEAF("Deaf", Formatting.GOLD),
    MUTED("Muted", Formatting.LIGHT_PURPLE);

    /** Human-readable name shown in the UI (e.g. "Blind"), as opposed to {@link #name()}. */
    private final String label;

    /** Minecraft named colour used to render this role in chat and on the HUD. */
    private final Formatting color;

    Role(String label, Formatting color) {
        this.label = label;
        this.color = color;
    }

    /** The display label, e.g. {@code "Blind"} (vs the all-caps {@link #name()}). */
    public String label() {
        return label;
    }

    /** The Minecraft named colour for this role (red = blind, gold = deaf, …). */
    public Formatting color() {
        return color;
    }

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
