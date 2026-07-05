package com.blinddeafmuted.common;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * Client -> server: a player's edited {@link ModConfig} from the slider menu. The server
 * accepts it as the new authoritative config, persists it, and re-broadcasts a
 * {@link ConfigPayload} so everyone (including the sender) converges on the same values.
 *
 * <p>Sending the whole config (not a single field) keeps the wire dead simple and is cheap —
 * 14 floats, sent only when a slider is released.
 */
public record ConfigUpdatePayload(ModConfig config) implements CustomPayload {

    public static final CustomPayload.Id<ConfigUpdatePayload> ID =
            new CustomPayload.Id<>(ModConstants.id("config_update"));

    public static final PacketCodec<PacketByteBuf, ConfigUpdatePayload> CODEC = PacketCodec.tuple(
            ModConfig.CODEC, ConfigUpdatePayload::config,
            ConfigUpdatePayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
