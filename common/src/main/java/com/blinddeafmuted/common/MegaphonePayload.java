package com.blinddeafmuted.common;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * Client -> server packet: the local player just started or stopped holding the
 * megaphone key (default {@code R}, push-to-megaphone). The server stores it
 * ({@code MegaphoneState}) so the voice-chat plugin renders this speaker loud and
 * saturated for DEAF listeners while {@code active} is true.
 *
 * <p>Sent only on transitions (press / release), not every tick. No protocol-version
 * field; the handshake already happens via {@link RolePayload} on join.
 */
public record MegaphonePayload(boolean active) implements CustomPayload {

    public static final CustomPayload.Id<MegaphonePayload> ID =
            new CustomPayload.Id<>(ModConstants.id("megaphone"));

    public static final PacketCodec<PacketByteBuf, MegaphonePayload> CODEC = PacketCodec.of(
            (value, buf) -> buf.writeBoolean(value.active()),
            buf -> new MegaphonePayload(buf.readBoolean())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
