package com.blinddeafmuted.server;

import com.blinddeafmuted.common.ModConstants;
import com.blinddeafmuted.common.ModItems;
import com.blinddeafmuted.common.Role;
import de.maxhenkel.voicechat.api.ServerPlayer;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.LocationalSoundPacketEvent;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.StaticSoundPacketEvent;
import de.maxhenkel.voicechat.api.packets.EntitySoundPacket;
import de.maxhenkel.voicechat.api.packets.LocationalSoundPacket;
import de.maxhenkel.voicechat.api.packets.MicrophonePacket;
import de.maxhenkel.voicechat.api.packets.SoundPacket;
import de.maxhenkel.voicechat.api.packets.StaticSoundPacket;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

/**
 * Enforces the {@link Role#DEAF} and {@link Role#MUTED} disabilities over
 * <em>voice</em>, by hooking the <a href="https://modrepo.de/minecraft/voicechat/api">
 * Simple Voice Chat</a> server plugin API.
 *
 * <p><b>Why server-side?</b> Simple Voice Chat relays every voice packet through the
 * server, so processing packets here enforces the rules for everyone — no client
 * cooperation needed. Originally both roles simply <em>cancelled</em> packets (full
 * silence). They now apply graduated audio effects instead, mirroring how BLIND has
 * a tight-fog variant rather than a pure blackout:
 * <ul>
 *   <li><b>MUTED</b> — we decode the speaker's {@link MicrophonePacketEvent}, garble
 *       it ({@link VoiceFx#distort}), and put the mangled audio back, so a muted
 *       player's voice leaks through distorted instead of being dropped entirely.</li>
 *   <li><b>DEAF</b> — for every sound packet addressed to a deaf <em>receiver</em>, we
 *       cancel the original and resend a re-rendered copy: near-silent normally, or
 *       loud and saturated if the <em>speaker</em> is holding a {@link ModItems#MEGAPHONE
 *       megaphone}. ({@link SoundPacket} has no opus setter, unlike
 *       {@link MicrophonePacket}, so DEAF must rebuild + resend rather than edit in
 *       place.)</li>
 * </ul>
 *
 * <p>If decoding ever fails we fall back to the old behaviour (cancel), so a bad frame
 * degrades to silence instead of crashing voice.
 *
 * <p><b>Soft dependency.</b> Simple Voice Chat is optional. This class is only loaded
 * via the {@code voicechat} entrypoint — if the voice mod isn't installed it is never
 * touched and the server runs normally (voice rules just aren't enforceable).
 *
 * <p>The {@link RoleManager} is provided statically via {@link #bind(RoleManager)} from
 * {@link BlindDeafMutedServer#onInitialize()}, because Simple Voice Chat constructs this
 * plugin itself (no-arg) and can't pass our manager in. The {@link VoicechatApi} /
 * {@link VoicechatServerApi} arrive via {@link #initialize(VoicechatApi)}.
 */
public final class BlindDeafMutedVoicechatPlugin implements VoicechatPlugin {

    /** Set once at server-mod init, read later when voice events fire. */
    private static volatile RoleManager roles;

    /** Set once at server-mod init: who is currently holding the megaphone key. */
    private static volatile MegaphoneState megaphones;

    /** SVC server API + our effect pipeline, set in {@link #initialize}. */
    private VoicechatServerApi serverApi;
    private VoiceFx fx;

    /** Guards against a resent DEAF packet re-triggering the same sound-packet event. */
    private final ThreadLocal<Boolean> rebuilding = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /** Wire in the role store. Called from {@link BlindDeafMutedServer#onInitialize()}. */
    public static void bind(RoleManager roleManager) {
        roles = roleManager;
    }

    /** Wire in the megaphone key-state store. Called from {@link BlindDeafMutedServer#onInitialize()}. */
    public static void bindMegaphone(MegaphoneState state) {
        megaphones = state;
    }

    @Override
    public String getPluginId() {
        return ModConstants.MOD_ID;
    }

    @Override
    public void initialize(VoicechatApi api) {
        // On a server, the api handed to a plugin is the server flavour.
        this.serverApi = (VoicechatServerApi) api;
        this.fx = new VoiceFx(api);
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        // MUTED: garble the speaker's microphone audio at the source.
        registration.registerEvent(MicrophonePacketEvent.class, this::onMicrophone);

        // DEAF: re-render any voice packet addressed to a deaf listener.
        registration.registerEvent(EntitySoundPacketEvent.class, this::onEntitySound);
        registration.registerEvent(LocationalSoundPacketEvent.class, this::onLocationalSound);
        registration.registerEvent(StaticSoundPacketEvent.class, this::onStaticSound);
    }

    // ---- MUTED -------------------------------------------------------------

    private void onMicrophone(MicrophonePacketEvent event) {
        VoicechatConnection sender = event.getSenderConnection();
        if (roleOf(sender) != Role.MUTED) {
            return;
        }
        MicrophonePacket packet = event.getPacket();
        UUID senderId = uuidOf(sender);
        boolean megaphone = megaphoneKeyDown(senderId) || holdsMegaphone(sender);
        byte[] garbled = fx.distort(senderId, packet.getOpusEncodedData(), megaphone);
        if (garbled != null) {
            packet.setOpusEncodedData(garbled);
        } else {
            event.cancel(); // decode failed → fall back to the old hard mute
        }
    }

    // ---- DEAF (one handler per concrete sound-packet type) -----------------

    private void onEntitySound(EntitySoundPacketEvent event) {
        if (skipDeaf(event.getReceiverConnection())) return;
        EntitySoundPacket p = event.getPacket();
        byte[] audio = renderForDeaf(event.getReceiverConnection(), event.getSenderConnection(), p);
        event.cancel();
        if (audio != null) {
            serverApi.sendEntitySoundPacketTo(event.getReceiverConnection(),
                    p.entitySoundPacketBuilder().opusEncodedData(audio).build());
        }
    }

    private void onLocationalSound(LocationalSoundPacketEvent event) {
        if (skipDeaf(event.getReceiverConnection())) return;
        LocationalSoundPacket p = event.getPacket();
        byte[] audio = renderForDeaf(event.getReceiverConnection(), event.getSenderConnection(), p);
        event.cancel();
        if (audio != null) {
            serverApi.sendLocationalSoundPacketTo(event.getReceiverConnection(),
                    p.locationalSoundPacketBuilder().opusEncodedData(audio).build());
        }
    }

    private void onStaticSound(StaticSoundPacketEvent event) {
        if (skipDeaf(event.getReceiverConnection())) return;
        StaticSoundPacket p = event.getPacket();
        byte[] audio = renderForDeaf(event.getReceiverConnection(), event.getSenderConnection(), p);
        event.cancel();
        if (audio != null) {
            serverApi.sendStaticSoundPacketTo(event.getReceiverConnection(),
                    p.staticSoundPacketBuilder().opusEncodedData(audio).build());
        }
    }

    /** True when this sound packet should be left untouched (receiver not deaf, or we're
     *  inside our own resend). */
    private boolean skipDeaf(VoicechatConnection receiver) {
        if (rebuilding.get()) return true;
        return roleOf(receiver) != Role.DEAF;
    }

    /** Decode→effect→encode a packet for a deaf receiver, picking the megaphone path when
     *  the speaker holds one. A muted speaker is kept faint instead of amplified.
     *  Returns null on decode failure (caller then just drops it). */
    private byte[] renderForDeaf(VoicechatConnection receiver, VoicechatConnection sender, SoundPacket packet) {
        UUID receiverId = uuidOf(receiver);
        UUID senderId = packet.getSender();
        if (receiverId == null || senderId == null) {
            return null;
        }
        // Megaphone is on if the speaker holds the item OR is pressing the megaphone key.
        boolean megaphone = megaphoneKeyDown(senderId) || holdsMegaphone(sender);
        boolean speakerMuted = roleOf(sender) == Role.MUTED;
        // Mark re-entrancy across the resend in case it re-fires the sound-packet event.
        rebuilding.set(Boolean.TRUE);
        try {
            return fx.forDeaf(receiverId, senderId, packet.getOpusEncodedData(), megaphone, speakerMuted);
        } finally {
            rebuilding.set(Boolean.FALSE);
        }
    }

    /** Whether the speaker is currently holding the push-to-megaphone key. */
    private static boolean megaphoneKeyDown(UUID senderId) {
        MegaphoneState state = megaphones;
        return state != null && state.isActive(senderId);
    }

    /** Whether the speaker is holding a {@link ModItems#MEGAPHONE} in either hand. */
    private boolean holdsMegaphone(VoicechatConnection sender) {
        ServerPlayerEntity player = serverPlayer(sender);
        if (player == null || ModItems.MEGAPHONE == null) {
            return false;
        }
        return isMegaphone(player.getMainHandStack()) || isMegaphone(player.getOffHandStack());
    }

    private static boolean isMegaphone(ItemStack stack) {
        return stack != null && stack.isOf(ModItems.MEGAPHONE);
    }

    // ---- shared helpers ----------------------------------------------------

    /** Resolve the role for a voice connection's player, defaulting to NONE. */
    private Role roleOf(VoicechatConnection connection) {
        RoleManager store = roles;
        UUID uuid = uuidOf(connection);
        return (store == null || uuid == null) ? Role.NONE : store.get(uuid);
    }

    private static UUID uuidOf(VoicechatConnection connection) {
        if (connection == null) return null;
        ServerPlayer player = connection.getPlayer();
        return player == null ? null : player.getUuid();
    }

    /** The vanilla {@link ServerPlayerEntity} behind an SVC connection, or null. */
    private static ServerPlayerEntity serverPlayer(VoicechatConnection connection) {
        if (connection == null) return null;
        ServerPlayer svc = connection.getPlayer();
        if (svc == null) return null;
        Object handle = svc.getPlayer(); // platform player object
        return (handle instanceof ServerPlayerEntity sp) ? sp : null;
    }
}
