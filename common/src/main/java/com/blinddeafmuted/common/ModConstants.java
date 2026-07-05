package com.blinddeafmuted.common;

import net.minecraft.util.Identifier;

/**
 * Shared constants for both sides.
 *
 * <p>{@link #PROTOCOL_VERSION} is the number compared during the client/server
 * handshake. Bump it whenever the {@link RolePayload} wire format changes, so a
 * mismatched client gets a clear "please update" message instead of a crash.
 */
public final class ModConstants {
    private ModConstants() {}

    public static final String MOD_ID = "blind-deaf-muted";

    /** Bump on ANY change to the network payload format.
     *  v2: added {@link TrackerPayload} (teammate HUD tracker).
     *  v3: added {@link RosterPayload} (who-is-what leaderboard HUD).
     *  v4: added {@link RollPayload} (roulette reveal animation trigger).
     *  v5: added {@link SkinVisibilityPayload} (toggle custom role accessories).
     *  v6: added {@link MegaphonePayload} (C2S push-to-megaphone) +
     *      {@link MegaphoneStatePayload} (S2C who's megaphoning, for the mouth model).
     *  v7: added {@link ConfigPayload} (S2C live tunables) +
     *      {@link ConfigUpdatePayload} (C2S slider-menu edits).
     *  v8: added the note card — {@link CardWritePayload} + {@link CardBrandishPayload}
     *      (C2S) and {@link CardBrandishStatePayload} (S2C) + the {@link ModComponents#CARD_TEXT}
     *      data component. */
    public static final int PROTOCOL_VERSION = 8;

    /** Helper to build identifiers under our namespace. */
    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }
}
