package com.blinddeafmuted.common;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Hand;

import java.util.ArrayList;
import java.util.List;

/**
 * Client -> server packet: the local player finished editing a note card in the
 * {@code CardEditScreen} and wants to save the text onto the held card. Modelled on
 * vanilla's book-update packet — the edit screen is client-only, so the authoritative
 * write to the {@link net.minecraft.item.ItemStack} happens server-side.
 *
 * <p>Carries which {@link Hand} holds the card (so the server writes to the right stack)
 * and the lines. The server clamps both count and length defensively. No protocol-version
 * field; the handshake already happens via {@link RolePayload} on join.
 */
public record CardWritePayload(Hand hand, List<String> lines) implements CustomPayload {

    public static final CustomPayload.Id<CardWritePayload> ID =
            new CustomPayload.Id<>(ModConstants.id("card_write"));

    public static final PacketCodec<PacketByteBuf, CardWritePayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeEnumConstant(value.hand());
                buf.writeVarInt(value.lines().size());
                for (String line : value.lines()) {
                    buf.writeString(line);
                }
            },
            buf -> {
                Hand hand = buf.readEnumConstant(Hand.class);
                int count = buf.readVarInt();
                List<String> list = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    list.add(buf.readString());
                }
                return new CardWritePayload(hand, list);
            }
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
