package com.blinddeafmuted.common;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * Server -> client packet that tells a client which {@link Role} it now has.
 *
 * <p>Sent on join and again whenever an admin changes the player's role, so the
 * client can switch effects live. Defined in :common so client and server share
 * the exact same wire format (this is why :common exists).
 *
 * <p>NOTE: this uses the 1.20.5+ {@link CustomPayload}/{@link PacketCodec} API.
 * If you retarget another MC version, verify these types still line up.
 */
public record RolePayload(int protocolVersion, Role role) implements CustomPayload {

    public static final CustomPayload.Id<RolePayload> ID =
            new CustomPayload.Id<>(ModConstants.id("role"));

    /** Encodes/decodes the payload: an int (protocol) + the role ordinal. */
    public static final PacketCodec<PacketByteBuf, RolePayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeVarInt(value.protocolVersion());
                buf.writeVarInt(value.role().ordinal());
            },
            buf -> new RolePayload(
                    buf.readVarInt(),
                    Role.values()[buf.readVarInt()]
            )
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
