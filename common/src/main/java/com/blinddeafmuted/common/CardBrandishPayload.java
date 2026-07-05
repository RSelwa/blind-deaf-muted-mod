package com.blinddeafmuted.common;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * Client -> server packet: the local player toggled "brandishing" their note card
 * (right-click, Sea-of-Thieves style). While {@code active}, the card is turned OUTWARD
 * so everyone <em>else</em> can read it (and the writer no longer sees it); while
 * inactive, only the writer sees it (private read).
 *
 * <p>Sent only on the toggle transition, not every tick. Counterpart to the outbound
 * {@link CardBrandishStatePayload} the server echoes back to all. No protocol-version
 * field; the handshake already happens via {@link RolePayload} on join.
 */
public record CardBrandishPayload(boolean active) implements CustomPayload {

    public static final CustomPayload.Id<CardBrandishPayload> ID =
            new CustomPayload.Id<>(ModConstants.id("card_brandish"));

    public static final PacketCodec<PacketByteBuf, CardBrandishPayload> CODEC = PacketCodec.of(
            (value, buf) -> buf.writeBoolean(value.active()),
            buf -> new CardBrandishPayload(buf.readBoolean())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
