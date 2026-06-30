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
 *       make out your immediate surroundings. The {@code ModItems#CANE cane} eases a
 *       blind player from {@code FOG_HARD} up to this while held.</li>
 *   <li>{@link #BLACKOUT_HUD} — the environment is painted solid black, but the HUD
 *       (hotbar, health, hunger, hand) and any open screen (inventory, etc.) remain
 *       visible. Done by {@code InGameHudMixin} drawing black BEFORE the HUD renders,
 *       so the HUD lands on top of the black.</li>
 *   <li>{@link #MYOPIA} — a depth-aware blur: the few blocks right in front of you
 *       stay sharp, everything past that smears into unreadable shapes (you see "a
 *       wall" but not which blocks). Driven by the {@code blind-deaf-muted:myopia}
 *       post-effect shader, toggled on/off by {@code MyopiaController}.</li>
 * </ul>
 *
 * <p>All looks are interchangeable for testing (cycle with the {@code B} key); the
 * fog pair is the gameplay default, with the cane easing HARD → MEDIUM.
 */
public enum BlindMode {
    FOG_HARD,
    FOG_MEDIUM,
    BLACKOUT_HUD,
    MYOPIA
}
