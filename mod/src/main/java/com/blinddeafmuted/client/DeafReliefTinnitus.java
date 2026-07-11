package com.blinddeafmuted.client;

import com.blinddeafmuted.common.Role;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Relief's downside for the DEAF player: while a DEAF player is under a Potion of Relief
 * (their hearing is temporarily restored), a constant tinnitus ringing plays in their own
 * ears — the deaf equivalent of the BLIND player's {@link ReliefNauseaController} nausea
 * wobble. Purely client-side and heard only by the local player.
 *
 * <p>Starts a single looping {@link TinnitusSoundInstance} when the local player becomes
 * DEAF-with-relief and stops it when that ends. The instance also self-terminates via its
 * own {@code tick()}, so a natural stop (role re-roll / relief expiry) is covered even if
 * this tick misses a frame; we just detect that ({@code !isPlaying}) and clear the ref.
 *
 * <p>The DEAF muffle low-pass ({@code SourceMixin}/{@code DeafAudioFilter}) does touch this
 * sound too, but during relief that filter is eased toward transparent, so the ringing is
 * heard clearly — which is the intent.
 */
public final class DeafReliefTinnitus {
    private DeafReliefTinnitus() {}

    private static TinnitusSoundInstance current;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean active = client.player != null
                    && RoleState.is(Role.DEAF)
                    && ReliefState.localActive();
            boolean playing = current != null && client.getSoundManager().isPlaying(current);

            if (active && !playing) {
                current = new TinnitusSoundInstance(client.player);
                client.getSoundManager().play(current);
            } else if (!active && current != null) {
                client.getSoundManager().stop(current);
                current = null;
            }
        });
    }
}
