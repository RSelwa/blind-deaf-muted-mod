package com.blinddeafmuted.client;

import com.blinddeafmuted.common.ModItems;
import com.blinddeafmuted.common.Role;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

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
     * let the player pick it — see {@link BlindMode}. Default: {@link BlindMode#FOG_HARD}
     * (tight ~2-block fog). Holding the {@link ModItems#CANE cane} eases it to the looser
     * {@link BlindMode#FOG_MEDIUM}; the {@code B} keybind also flips it manually for testing.
     */
    private static volatile BlindMode blindMode = BlindMode.FOG_HARD;

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

    /**
     * The blind look to actually render right now. The cane mechanic lives here: in a fog
     * mode, holding the {@link ModItems#CANE cane} eases {@link BlindMode#FOG_HARD} up to
     * the looser {@link BlindMode#FOG_MEDIUM} — no cane → near-blind tight fog; cane in
     * hand → you can make out your surroundings. The non-fog looks ({@link
     * BlindMode#BLACKOUT_HUD}, {@link BlindMode#MYOPIA}) are manual {@code B}-cycle test
     * styles and pass through unchanged. Read locally — no networking. The effect sites
     * gate on this instead of {@link #getBlindMode()}.
     */
    public static BlindMode effectiveBlindMode() {
        switch (blindMode) {
            // Manual test looks (B-cycle): shown as-is, no cane upgrade.
            case MYOPIA:
            case BLACKOUT_HUD:
            case FOG_MEDIUM:
                return blindMode;
            // Default fog: the cane eases HARD → MEDIUM while held.
            case FOG_HARD:
            default:
                return localHoldsCane() ? BlindMode.FOG_MEDIUM : BlindMode.FOG_HARD;
        }
    }

    /** Whether the local player is holding the cane item in either hand. */
    private static boolean localHoldsCane() {
        if (ModItems.CANE == null) return false;
        PlayerEntity player = MinecraftClient.getInstance().player;
        return player != null
                && (player.getMainHandStack().isOf(ModItems.CANE)
                || player.getOffHandStack().isOf(ModItems.CANE));
    }

    /** Cycle to the next blind mode (wired to a keybind for live testing). */
    public static void toggleBlindMode() {
        BlindMode[] all = BlindMode.values();
        blindMode = all[(blindMode.ordinal() + 1) % all.length];
    }
}
