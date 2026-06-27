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
 * </ul>
 */
public enum BlindMode {
    VANILLA,
    BLACKOUT_HUD
}
