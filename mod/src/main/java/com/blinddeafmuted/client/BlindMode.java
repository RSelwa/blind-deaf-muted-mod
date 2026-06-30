package com.blinddeafmuted.client;

/**
 * The two visual styles of the BLIND role. Both stop the player from seeing the
 * environment; they differ only in HOW.
 *
 * <ul>
 *   <li>{@link #VANILLA} — apply Minecraft's own Blindness effect (the familiar
 *       closing-in fog). Handled by {@code BlindHandler} re-applying the status
 *       effect to the local player.</li>
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
 * <p>All three are interchangeable looks; we keep them all so we can test in-game and
 * later promote one to the enforced default.
 */
public enum BlindMode {
    VANILLA,
    BLACKOUT_HUD,
    MYOPIA
}
