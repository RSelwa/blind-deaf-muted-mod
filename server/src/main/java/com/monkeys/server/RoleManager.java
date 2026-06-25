package com.monkeys.server;

import com.monkeys.common.ModConstants;
import com.monkeys.common.Role;
import com.monkeys.common.RolePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks each player's {@link Role} and pushes changes to their client.
 *
 * <p>This is the single place that owns "who is what". The command layer asks it
 * to change roles; it stores the value and sends a {@link RolePayload}.
 *
 * <p>TODO: persist this map (PlayerData / world save) so roles survive a restart;
 * right now it's in-memory only.
 */
public class RoleManager {
    private final Map<UUID, Role> roles = new HashMap<>();

    public Role get(ServerPlayerEntity player) {
        return get(player.getUuid());
    }

    /**
     * Look up a role by UUID. Used by the voice-chat integration, which only has
     * the player's UUID (via the Simple Voice Chat connection), not the
     * {@link ServerPlayerEntity}. Unknown players default to {@link Role#NONE}.
     */
    public Role get(UUID uuid) {
        return roles.getOrDefault(uuid, Role.NONE);
    }

    /** Set a player's role and immediately sync it to their client. */
    public void set(ServerPlayerEntity player, Role role) {
        roles.put(player.getUuid(), role);
        sync(player);
    }

    /** Send the player's current role to their client. */
    public void sync(ServerPlayerEntity player) {
        Role role = get(player);
        ServerPlayNetworking.send(player, new RolePayload(ModConstants.PROTOCOL_VERSION, role));
    }
}
