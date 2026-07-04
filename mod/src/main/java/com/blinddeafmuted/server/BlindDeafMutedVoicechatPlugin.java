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

    /** Guards against a resent packet re-triggering the same sound-packet event. */
    private final ThreadLocal<Boolean> rebuilding = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /** Sentinel from {@link #renderPacket}: leave the packet untouched (not our concern) —
     *  distinct from {@code null}, which means "drop it" (deaf decode failure → silence). */
    private static final byte[] UNTOUCHED = new byte[0];

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

    // ---- Sound packets: DEAF re-render + non-deaf megaphone bullhorn -------
    // One handler per concrete sound-packet type; all defer to renderPacket() for the
    // decision, then cancel + resend the reprocessed audio (guarding the re-entrant resend).

    private void onEntitySound(EntitySoundPacketEvent event) {
        if (rebuilding.get()) return;
        EntitySoundPacket p = event.getPacket();
        byte[] audio = renderPacket(event.getReceiverConnection(), event.getSenderConnection(), p);
        if (audio == UNTOUCHED) return;
        event.cancel();
        if (audio != null) {
            // Guard the resend: SVC re-fires the sound event for the resent packet on this
            // same thread, so without the flag we'd recurse into this handler forever.
            rebuilding.set(Boolean.TRUE);
            try {
                serverApi.sendEntitySoundPacketTo(event.getReceiverConnection(),
                        p.entitySoundPacketBuilder().opusEncodedData(audio).build());
            } finally {
                rebuilding.set(Boolean.FALSE);
            }
        }
    }

    private void onLocationalSound(LocationalSoundPacketEvent event) {
        if (rebuilding.get()) return;
        LocationalSoundPacket p = event.getPacket();
        byte[] audio = renderPacket(event.getReceiverConnection(), event.getSenderConnection(), p);
        if (audio == UNTOUCHED) return;
        event.cancel();
        if (audio != null) {
            rebuilding.set(Boolean.TRUE);
            try {
                serverApi.sendLocationalSoundPacketTo(event.getReceiverConnection(),
                        p.locationalSoundPacketBuilder().opusEncodedData(audio).build());
            } finally {
                rebuilding.set(Boolean.FALSE);
            }
        }
    }

    private void onStaticSound(StaticSoundPacketEvent event) {
        if (rebuilding.get()) return;
        StaticSoundPacket p = event.getPacket();
        byte[] audio = renderPacket(event.getReceiverConnection(), event.getSenderConnection(), p);
        if (audio == UNTOUCHED) return;
        event.cancel();
        if (audio != null) {
            rebuilding.set(Boolean.TRUE);
            try {
                serverApi.sendStaticSoundPacketTo(event.getReceiverConnection(),
                        p.staticSoundPacketBuilder().opusEncodedData(audio).build());
            } finally {
                rebuilding.set(Boolean.FALSE);
            }
        }
    }

    /**
     * Decide how one sound packet should be reprocessed for its receiver:
     * <ul>
     *   <li><b>Deaf receiver</b> — always re-rendered: megaphone → clean/clear (the deaf
     *       accessibility channel), otherwise the "in a box" muffle. Returns {@code null} on
     *       decode failure so the caller drops it (silence), preserving deaf enforcement.</li>
     *   <li><b>Non-deaf receiver + NON-muted speaker megaphoning</b> — the fun bullhorn
     *       saturation. (A muted speaker is already saturated at the mic source, so its
     *       megaphone reaches bystanders that way; re-doing it here would double it.)</li>
     *   <li><b>Everyone else</b> — {@link #UNTOUCHED} (pass the raw voice through).</li>
     * </ul>
     */
    private byte[] renderPacket(VoicechatConnection receiver, VoicechatConnection sender, SoundPacket packet) {
        UUID receiverId = uuidOf(receiver);
        UUID senderId = packet.getSender();
        if (receiverId == null || senderId == null) {
            return UNTOUCHED;
        }
        // Megaphone is on if the speaker holds the item OR is pressing the megaphone key.
        boolean megaphone = megaphoneKeyDown(senderId) || holdsMegaphone(sender);
        boolean speakerMuted = roleOf(sender) == Role.MUTED;

        if (roleOf(receiver) == Role.DEAF) {
            // A muted speaker never gets the megaphone clarity boost to a deaf listener — mute
            // wins (VoiceFx keeps it muffled even with a megaphone).
            return fx.forDeaf(receiverId, senderId, packet.getOpusEncodedData(), megaphone, speakerMuted);
        }
        if (megaphone && !speakerMuted) {
            byte[] out = fx.forMegaphoneBystander(receiverId, senderId, packet.getOpusEncodedData());
            return out == null ? UNTOUCHED : out; // decode fail → leave the raw voice
        }
        return UNTOUCHED;
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
