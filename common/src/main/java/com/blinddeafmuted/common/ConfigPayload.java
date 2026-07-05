package com.blinddeafmuted.common;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * Server -> client: the current live {@link ModConfig}. Broadcast on join and after any
 * change, so every client's mirror ({@code ClientConfigState}) stays in sync and the slider
 * menu opens showing the real current values.
 *
 * <p>Like {@link RosterPayload} it omits the protocol-version field — the handshake already
 * rides {@link RolePayload} on join.
 */
public record ConfigPayload(ModConfig config) implements CustomPayload {

    public static final CustomPayload.Id<ConfigPayload> ID =
            new CustomPayload.Id<>(ModConstants.id("config"));

    public static final PacketCodec<PacketByteBuf, ConfigPayload> CODEC = PacketCodec.tuple(
            ModConfig.CODEC, ConfigPayload::config,
            ConfigPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
