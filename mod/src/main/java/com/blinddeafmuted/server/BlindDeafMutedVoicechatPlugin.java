package com.blinddeafmuted.server;

import com.blinddeafmuted.common.ModConstants;
import com.blinddeafmuted.common.Role;
import de.maxhenkel.voicechat.api.ServerPlayer;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.LocationalSoundPacketEvent;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.StaticSoundPacketEvent;

import java.util.UUID;

/**
 * Enforces the {@link Role#DEAF} and {@link Role#MUTED} disabilities over
 * <em>voice</em>, by hooking the <a href="https://modrepo.de/minecraft/voicechat/api">
 * Simple Voice Chat</a> server plugin API.
 *
 * <p><b>Why server-side?</b> Simple Voice Chat relays every voice packet through
 * the server, so cancelling packets here enforces the rules for everyone — no
 * client cooperation needed. This is what upgrades voice comms from the
 * "honor-system" note in {@code DESIGN.md} to actually enforced:
 * <ul>
 *   <li><b>MUTED</b> — we cancel the speaker's {@link MicrophonePacketEvent}, so
 *       their microphone audio is dropped at the server before it reaches anyone.</li>
 *   <li><b>DEAF</b> — we cancel any sound packet whose <em>receiver</em> is deaf
 *       ({@link EntitySoundPacketEvent} / {@link LocationalSoundPacketEvent} /
 *       {@link StaticSoundPacketEvent}, which between them cover proximity, group,
 *       entity and spectator audio), so a deaf player hears no voice.</li>
 * </ul>
 *
 * <p><b>Soft dependency.</b> Simple Voice Chat is optional. This class is only
 * loaded via the {@code voicechat} entrypoint, which the voice-chat mod alone
 * reads — if it isn't installed, this class is never touched and the server runs
 * normally (voice rules just aren't enforceable, matching the Discord fallback).
 *
 * <p>The {@link RoleManager} is provided statically via {@link #bind(RoleManager)}
 * from {@link BlindDeafMutedServer#onInitialize()}, because Simple Voice Chat constructs
 * this plugin itself (no-arg) and can't pass our manager in.
 */
public final class BlindDeafMutedVoicechatPlugin implements VoicechatPlugin {

    /** Set once at server-mod init, read later when voice events fire. */
    private static volatile RoleManager roles;

    /** Wire in the role store. Called from {@link BlindDeafMutedServer#onInitialize()}. */
    public static void bind(RoleManager roleManager) {
        roles = roleManager;
    }

    @Override
    public String getPluginId() {
        return ModConstants.MOD_ID;
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        // MUTED: drop the speaker's microphone packet at the source.
        registration.registerEvent(MicrophonePacketEvent.class, this::onMicrophone);

        // DEAF: drop any voice packet addressed to a deaf listener. Each lambda
        // is typed to its concrete event, so getReceiverConnection() and cancel()
        // (both inherited from the PacketEvent base) resolve without any casts.
        registration.registerEvent(EntitySoundPacketEvent.class,
                e -> cancelIfDeaf(e.getReceiverConnection(), e::cancel));
        registration.registerEvent(LocationalSoundPacketEvent.class,
                e -> cancelIfDeaf(e.getReceiverConnection(), e::cancel));
        registration.registerEvent(StaticSoundPacketEvent.class,
                e -> cancelIfDeaf(e.getReceiverConnection(), e::cancel));
    }

    private void onMicrophone(MicrophonePacketEvent event) {
        if (roleOf(event.getSenderConnection()) == Role.MUTED) {
            event.cancel();
        }
    }

    /** Cancel a sound packet (via the supplied {@code cancel} action) if its receiver is deaf. */
    private void cancelIfDeaf(VoicechatConnection receiver, Runnable cancel) {
        if (roleOf(receiver) == Role.DEAF) {
            cancel.run();
        }
    }

    /** Resolve the role for a voice connection's player, defaulting to NONE. */
    private Role roleOf(VoicechatConnection connection) {
        RoleManager store = roles;
        if (store == null || connection == null) {
            return Role.NONE;
        }
        ServerPlayer player = connection.getPlayer();
        if (player == null) {
            return Role.NONE;
        }
        UUID uuid = player.getUuid();
        return uuid == null ? Role.NONE : store.get(uuid);
    }
}
