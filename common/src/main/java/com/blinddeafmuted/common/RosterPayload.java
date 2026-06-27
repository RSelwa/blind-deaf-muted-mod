package com.blinddeafmuted.common;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> client packet carrying the full team roster — every online player and
 * the {@link Role} they currently hold — so each client can draw the "who is what"
 * leaderboard HUD in the top-right corner (see {@code RosterHud}).
 *
 * <p>Like {@link TrackerPayload}, a client can't build this on its own: it only
 * knows the roles of players loaded around it (and not even reliably), so the
 * server — the single owner of "who is what" via {@code RoleManager} — pushes the
 * whole list. It's small and changes rarely, so it's broadcast on a slow tick.
 *
 * <p>Also like {@link TrackerPayload}, it omits the protocol-version field; the
 * version handshake already happens via {@link RolePayload} on join.
 */
public record RosterPayload(List<Entry> entries) implements CustomPayload {

    /** One roster row: a player's display name and their current role. */
    public record Entry(String name, Role role) {}

    public static final CustomPayload.Id<RosterPayload> ID =
            new CustomPayload.Id<>(ModConstants.id("roster"));

    public static final PacketCodec<PacketByteBuf, RosterPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeVarInt(value.entries().size());
                for (Entry e : value.entries()) {
                    buf.writeString(e.name());
                    // Send the role as its ordinal; client/server share the enum
                    // (the `common` module), so the ordinal is stable per protocol.
                    buf.writeVarInt(e.role().ordinal());
                }
            },
            buf -> {
                int count = buf.readVarInt();
                List<Entry> list = new ArrayList<>(count);
                Role[] roles = Role.values();
                for (int i = 0; i < count; i++) {
                    String name = buf.readString();
                    int ordinal = buf.readVarInt();
                    // Defensive: an out-of-range ordinal (version skew) falls back to NONE.
                    Role role = (ordinal >= 0 && ordinal < roles.length) ? roles[ordinal] : Role.NONE;
                    list.add(new Entry(name, role));
                }
                return new RosterPayload(list);
            }
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
