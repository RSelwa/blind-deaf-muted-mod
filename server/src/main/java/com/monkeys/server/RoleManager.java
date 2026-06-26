package com.monkeys.server;

import com.monkeys.common.ModConstants;
import com.monkeys.common.Role;
import com.monkeys.common.RolePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

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

    /** Set a player's role, tell them about it, and immediately sync it to their client. */
    public void set(ServerPlayerEntity player, Role role) {
        roles.put(player.getUuid(), role);
        announce(player, role);
        sync(player);
    }

    /**
     * Send the player a coloured "You're now …" chat message announcing their new
     * role (e.g. <span style="color:red">You're now BLIND</span>). Clearing back to
     * {@link Role#NONE} reads as a recovery message instead.
     *
     * <p>This is the personal, in-your-face announcement; the admin who ran the
     * command gets a separate confirmation via the command's own feedback.
     */
    private void announce(ServerPlayerEntity player, Role role) {
        Text message;
        if (role == Role.NONE) {
            message = Text.literal("Your disability has been cleared — you're back to normal.")
                    .formatted(Formatting.GRAY);
        } else {
            message = Text.literal("You're now ")
                    .formatted(Formatting.WHITE)
                    .append(Text.literal(role.name())
                            .formatted(role.color(), Formatting.BOLD));
        }
        player.sendMessage(message, false); // false = chat line, not the action bar
    }

    /** Send the player's current role to their client. */
    public void sync(ServerPlayerEntity player) {
        Role role = get(player);
        ServerPlayNetworking.send(player, new RolePayload(ModConstants.PROTOCOL_VERSION, role));
    }
}
