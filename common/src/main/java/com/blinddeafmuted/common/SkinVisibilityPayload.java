package com.blinddeafmuted.common;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * Server -> client packet carrying whether the mod's custom role "skins" — the cane,
 * glasses, bandage and headset accessories drawn by the feature renderers — should be
 * shown. Toggled by an op via {@code /bdm skin <on|off>}; the server owns the flag
 * ({@code SkinVisibilityManager}) and broadcasts it so every client renders alike.
 *
 * <p>Like {@link RosterPayload}, it omits the protocol-version field; the version
 * handshake already happens via {@link RolePayload} on join.
 */
public record SkinVisibilityPayload(boolean enabled) implements CustomPayload {

    public static final CustomPayload.Id<SkinVisibilityPayload> ID =
            new CustomPayload.Id<>(ModConstants.id("skin_visibility"));

    public static final PacketCodec<PacketByteBuf, SkinVisibilityPayload> CODEC = PacketCodec.of(
            (value, buf) -> buf.writeBoolean(value.enabled()),
            buf -> new SkinVisibilityPayload(buf.readBoolean())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
