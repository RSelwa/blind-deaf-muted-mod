package com.blinddeafmuted.client;

import com.blinddeafmuted.common.Role;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;

/**
 * MUTED effect: block the player from sending chat (and commands meant as chat).
 *
 * <p>Blocking outgoing chat client-side is instant and clean. Note: this only
 * covers in-game text. Voice (Discord / proximity-voice) can't be enforced from
 * here — that's the comms-mode caveat in DESIGN.md §4.
 */
public final class MuteHandler {
    private MuteHandler() {}

    public static void register() {
        // ALLOW_CHAT: return false to cancel the outgoing chat message.
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> !RoleState.is(Role.MUTED));

        // TODO: optionally also show a "you are MUTED" toast when a message is
        // swallowed, so the player understands why nothing was sent.
    }
}
