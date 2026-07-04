package com.blinddeafmuted.common;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> client packet carrying the live positions of the recipient's teammates,
 * so the client can draw the on-HUD teammate tracker (name · distance · direction
 * arrow) above the health/food bar.
 *
 * <p>Why a packet (and not just reading nearby entities client-side)? A client only
 * knows the positions of players currently loaded around it. Teammates across the
 * world aren't loaded, so the server — which knows everyone — pushes the list a few
 * times per second. See {@code DEVELOPER.md} → "Teammate tracker".
 *
 * <p>Sent frequently, so it deliberately omits the protocol-version field that
 * {@link RolePayload} carries; the version handshake already happens via RolePayload
 * on join.
 */
public record TrackerPayload(List<Entry> entries) implements CustomPayload {

    /** One tracked teammate: their display name, world position, and the id of the
     *  dimension they're in (e.g. {@code minecraft:the_nether}) so the client can flag
     *  teammates in another dimension. */
    public record Entry(String name, double x, double y, double z, String dimension) {}

    public static final CustomPayload.Id<TrackerPayload> ID =
            new CustomPayload.Id<>(ModConstants.id("tracker"));

    public static final PacketCodec<PacketByteBuf, TrackerPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeVarInt(value.entries().size());
                for (Entry e : value.entries()) {
                    buf.writeString(e.name());
                    buf.writeDouble(e.x());
                    buf.writeDouble(e.y());
                    buf.writeDouble(e.z());
                    buf.writeString(e.dimension());
                }
            },
            buf -> {
                int count = buf.readVarInt();
                List<Entry> list = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    String name = buf.readString();
                    double x = buf.readDouble();
                    double y = buf.readDouble();
                    double z = buf.readDouble();
                    String dimension = buf.readString();
                    list.add(new Entry(name, x, y, z, dimension));
                }
                return new TrackerPayload(list);
            }
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
