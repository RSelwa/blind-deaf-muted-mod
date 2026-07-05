package com.blinddeafmuted.common;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> client packet listing the display names of every player currently under a
 * Potion of Relief (see {@link ReliefPotionEntity}). Broadcast to everyone (on the slow
 * roster tick, plus immediately when a potion shatters) so each client can scale down its
 * own disability effects while its name is in the set.
 *
 * <p>The reduction amount itself isn't sent here — it's the live {@code reliefReductionPercent}
 * knob already carried by {@link ConfigPayload}, so clients read it from their config mirror.
 * Same broadcast shape as {@link MegaphoneStatePayload}.
 */
public record ReliefPayload(List<String> relievedNames) implements CustomPayload {

    public static final CustomPayload.Id<ReliefPayload> ID =
            new CustomPayload.Id<>(ModConstants.id("relief_state"));

    public static final PacketCodec<PacketByteBuf, ReliefPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeVarInt(value.relievedNames().size());
                for (String name : value.relievedNames()) {
                    buf.writeString(name);
                }
            },
            buf -> {
                int count = buf.readVarInt();
                List<String> list = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    list.add(buf.readString());
                }
                return new ReliefPayload(list);
            }
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
