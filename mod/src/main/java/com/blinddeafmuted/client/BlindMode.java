package com.blinddeafmuted.client;

/**
 * The visual styles of the BLIND role. They all stop the player from seeing the
 * environment; they differ only in HOW (and how harshly).
 *
 * <ul>
 *   <li>{@link #FOG_HARD} — Minecraft's Blindness effect plus a very tight fog (~2
 *       blocks): you basically see only your feet. This is the default blind look.
 *       Handled by {@code BlindHandler} (re-applies Blindness) + {@code
 *       BackgroundRendererMixin} (tightens the fog).</li>
 *   <li>{@link #FOG_MEDIUM} — the same idea but a looser fog (~7 blocks), so you can
 *       make out your immediate surroundings. A manual {@code B}-cycle look; the cane no
 *       longer maps here (it now switches to {@link #MYOPIA}).</li>
 *   <li>{@link #BLACKOUT_HUD} — the environment is painted solid black, but the HUD
 *       (hotbar, health, hunger, hand) and any open screen (inventory, etc.) remain
 *       visible. Done by {@code InGameHudMixin} drawing black BEFORE the HUD renders,
 *       so the HUD lands on top of the black.</li>
 *   <li>{@link #MYOPIA} — SOFT depth-aware blur (the cane look): the few blocks right
 *       in front of you stay sharp, everything past smears into shapes, and a generous
 *       clear hole keeps usable sight. Driven by the {@code blind-deaf-muted:myopia}
 *       post-effect shader (Intensity 0), toggled by {@code MyopiaController}.</li>
 *   <li>{@link #MYOPIA_HARD} — the same shader at Intensity 1 (pipeline {@code
 *       blind-deaf-muted:myopia_hard}): a tiny clear hole, heavy blur that starts almost
 *       at the camera, near-black surround. This is the default blind look with NO cane.</li>
 * </ul>
 *
 * <p><b>Gameplay path</b> (see {@code RoleState#effectiveBlindMode}): blind → myopia
 * always — {@link #MYOPIA_HARD} without the cane, {@link #MYOPIA} (soft) while it's held.
 * {@link #FOG_HARD}, {@link #FOG_MEDIUM} and {@link #BLACKOUT_HUD} are no longer on the
 * gameplay path; the code is kept and they remain reachable via the {@code B} test cycle.
 */
public enum BlindMode {
    FOG_HARD,
    FOG_MEDIUM,
    BLACKOUT_HUD,
    MYOPIA,
    MYOPIA_HARD
}
