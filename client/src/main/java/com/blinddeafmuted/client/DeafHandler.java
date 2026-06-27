package com.blinddeafmuted.client;

import com.blinddeafmuted.common.Role;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * DEAF effect: total silence, driven entirely by the mod (never by user settings).
 *
 * <p>Two halves work together:
 * <ul>
 *   <li>{@code SoundSystemMixin} cancels every <i>new</i> sound while deaf — so
 *       nothing fresh ever starts (mobs, music, UI, footsteps).</li>
 *   <li>This handler kills every <i>already-playing</i> sound at the moment the
 *       player becomes deaf (the music track, looping cave/mob ambience, etc.), so
 *       there's no lingering audio.</li>
 * </ul>
 *
 * <p>Crucially we do NOT touch {@code client.options} volumes. That means switching
 * a player between roles is instant and lossless — their real volume preferences are
 * never altered, so there's nothing to "restore" and nothing to get out of sync when
 * an admin shuffles teams mid-game. When deafness ends, sound simply resumes.
 */
public final class DeafHandler {
    private DeafHandler() {}

    private static boolean wasDeaf = false;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean deaf = RoleState.is(Role.DEAF);
            if (deaf == wasDeaf) return; // only act on transitions
            wasDeaf = deaf;

            if (deaf && client.getSoundManager() != null) {
                // Cut off everything currently audible; the mixin handles everything after.
                client.getSoundManager().stopAll();
            }
            // On un-deaf: nothing to do — the mixin stops cancelling and sound resumes.
        });
    }
}
