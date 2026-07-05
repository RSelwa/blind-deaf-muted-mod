package com.blinddeafmuted.common;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> client packet listing the display names of every player currently brandishing
 * a note card outward. Broadcast to everyone (on the slow roster tick, plus immediately on
 * each toggle) so each client knows to flip that player's card FACE toward viewers.
 *
 * <p>The card TEXT itself is not sent here — it rides on the tracked held {@link
 * net.minecraft.item.ItemStack}'s {@link ModComponents#CARD_TEXT} component, so nearby
 * clients already have it. This packet only carries the who's-showing set. Same broadcast
 * shape as {@link MegaphoneStatePayload}.
 */
public record CardBrandishStatePayload(List<String> activeNames) implements CustomPayload {

    public static final CustomPayload.Id<CardBrandishStatePayload> ID =
            new CustomPayload.Id<>(ModConstants.id("card_brandish_state"));

    public static final PacketCodec<PacketByteBuf, CardBrandishStatePayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeVarInt(value.activeNames().size());
                for (String name : value.activeNames()) {
                    buf.writeString(name);
                }
            },
            buf -> {
                int count = buf.readVarInt();
                List<String> list = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    list.add(buf.readString());
                }
                return new CardBrandishStatePayload(list);
            }
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
