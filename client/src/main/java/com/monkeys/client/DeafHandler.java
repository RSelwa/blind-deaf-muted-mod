package com.monkeys.client;

import com.monkeys.common.Role;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.sound.SoundCategory;

/**
 * DEAF effect: silence ALL audio locally.
 *
 * <p>Because this is client-side, we can kill every sound category — including the
 * client-only sounds (own footsteps, UI, music) a server could never reach. That's
 * the whole reason effects live on the client (DESIGN.md §3).
 *
 * <p>Simple approach below: each tick, force every category's volume to 0 while
 * deaf, and remember/restore the player's own settings when not. A cleaner long-term
 * approach is a Mixin on SoundSystem to drop sounds at the source — left as a TODO.
 */
public final class DeafHandler {
    private DeafHandler() {}

    private static boolean wasDeaf = false;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.options == null) return;

            boolean deaf = RoleState.is(Role.DEAF);
            if (deaf == wasDeaf) return; // only act on transitions
            wasDeaf = deaf;

            if (deaf) {
                // TODO: snapshot current per-category volumes first so we can
                // restore the player's real settings afterwards.
                for (SoundCategory category : SoundCategory.values()) {
                    client.options.getSoundVolumeOption(category).setValue(0.0);
                }
            } else {
                // TODO: restore the snapshot taken above instead of forcing 1.0.
                for (SoundCategory category : SoundCategory.values()) {
                    client.options.getSoundVolumeOption(category).setValue(1.0);
                }
            }
        });
    }
}
