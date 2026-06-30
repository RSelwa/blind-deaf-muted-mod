package com.blinddeafmuted.common;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> client packet listing the display names of every player currently holding
 * the megaphone key. Broadcast to everyone (on the slow roster tick) so each client can
 * draw the megaphone-at-the-mouth model on those players (see {@code MegaphoneFeatureRenderer}).
 *
 * <p>Counterpart to the inbound {@link MegaphonePayload}: clients report their own
 * press/release, the server collects them and echoes the full active set back to all,
 * the same broadcast shape as {@link RosterPayload}. No protocol-version field; the
 * handshake already happens via {@link RolePayload} on join.
 */
public record MegaphoneStatePayload(List<String> activeNames) implements CustomPayload {

    public static final CustomPayload.Id<MegaphoneStatePayload> ID =
            new CustomPayload.Id<>(ModConstants.id("megaphone_state"));

    public static final PacketCodec<PacketByteBuf, MegaphoneStatePayload> CODEC = PacketCodec.of(
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
                return new MegaphoneStatePayload(list);
            }
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
