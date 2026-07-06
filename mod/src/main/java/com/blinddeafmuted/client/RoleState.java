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
     * let the player pick it — see {@link BlindMode}. Default: {@link BlindMode#MYOPIA}
     * (the auto/gameplay look): without the {@link ModItems#CANE cane} it renders as the
     * harsh {@link BlindMode#MYOPIA_HARD}, holding the cane eases it to the soft
     * {@link BlindMode#MYOPIA}. The {@code B} keybind flips it manually for testing
     * (incl. the kept-but-off-path fog/blackout looks).
     */
    private static volatile BlindMode blindMode = BlindMode.MYOPIA;

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
     * The blind look to actually render right now. The cane mechanic lives here: the
     * gameplay look is {@link BlindMode#MYOPIA} and the cane picks the intensity — no cane
     * → harsh {@link BlindMode#MYOPIA_HARD} (tiny hole, heavy blur, near-black surround);
     * cane in hand → soft {@link BlindMode#MYOPIA} (generous hole, usable sight). The
     * kept-but-off-path test looks ({@link BlindMode#FOG_HARD}, {@link BlindMode#FOG_MEDIUM},
     * {@link BlindMode#BLACKOUT_HUD}) and a manually forced {@link BlindMode#MYOPIA_HARD}
     * pass through unchanged. Read locally — no networking. The effect sites gate on this
     * instead of {@link #getBlindMode()}.
     */
    public static BlindMode effectiveBlindMode() {
        switch (blindMode) {
            // Gameplay default: cane picks soft (MYOPIA) vs harsh (MYOPIA_HARD). A Potion of
            // Relief is handled downstream in MyopiaController (a third near-clear pipeline
            // that overrides either step while the relief effect is active).
            case MYOPIA:
                return localHoldsCane() ? BlindMode.MYOPIA : BlindMode.MYOPIA_HARD;
            // Manual B-cycle test looks (kept, but off the gameplay path): shown as-is.
            case MYOPIA_HARD:
            case BLACKOUT_HUD:
            case FOG_MEDIUM:
            case FOG_HARD:
            default:
                return blindMode;
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
