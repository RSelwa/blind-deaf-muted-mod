package com.monkeys.common;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * Server -> client packet that triggers the client-side "roulette" reveal animation
 * for a freshly-assigned {@link Role} (see {@code RouletteAnimation}).
 *
 * <p>Unlike {@link RolePayload} — which applies the role's effect immediately and is
 * used for joins and manual admin {@code /monkeys set} — this is sent by the random
 * roll (and the future re-roll bottle). The client spins a slot machine through the
 * roles, lands on {@link #role()}, and only then applies the effect, so the player
 * gets to watch their own reveal (a blind player would otherwise black out instantly).
 *
 * <p>The server has already stored the role by the time this is sent, so server-side
 * enforcement (voice chat) and the roster HUD are correct right away; only the local
 * visual effect is deferred to the end of the animation.
 */
public record RollPayload(Role role) implements CustomPayload {

    public static final CustomPayload.Id<RollPayload> ID =
            new CustomPayload.Id<>(ModConstants.id("roll"));

    public static final PacketCodec<PacketByteBuf, RollPayload> CODEC = PacketCodec.of(
            (value, buf) -> buf.writeVarInt(value.role().ordinal()),
            buf -> {
                int ordinal = buf.readVarInt();
                Role[] roles = Role.values();
                Role role = (ordinal >= 0 && ordinal < roles.length) ? roles[ordinal] : Role.NONE;
                return new RollPayload(role);
            }
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
