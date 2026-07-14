package com.blinddeafmuted.server;

import com.blinddeafmuted.common.ModConstants;
import com.blinddeafmuted.common.Role;
import com.blinddeafmuted.common.RolePayload;
import com.blinddeafmuted.common.RollPayload;
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

    /** How long the client roulette animation runs (65 ticks) plus a little slack — while
     *  this window is open the vanilla roster sidebar must NOT refresh (anti-spoiler). */
    private static final long ROULETTE_FREEZE_MS = 3_500L;
    private volatile long lastAnimatedAtMs = 0L;

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
     * Like {@link #set} but plays the client-side roulette reveal instead of applying
     * the role instantly. Used by the random roll (and the future re-roll bottle).
     *
     * <p>The role is stored immediately — so the roster HUD and voice-chat enforcement
     * are correct right away — but the client defers the visual effect to the end of
     * the animation and shows its own "You're now …" reveal, so we skip both the chat
     * {@link #announce} and the instant {@link #sync} here.
     */
    public void setAnimated(ServerPlayerEntity player, Role role) {
        roles.put(player.getUuid(), role);
        lastAnimatedAtMs = System.currentTimeMillis();
        ServerPlayNetworking.send(player, new RollPayload(role));
    }

    /** True while the client-side roulette reveal is (probably) still playing — the roster
     *  sidebar holds its old lines during this window so the roll isn't spoiled early. */
    public boolean isRouletteRunning() {
        return System.currentTimeMillis() - lastAnimatedAtMs < ROULETTE_FREEZE_MS;
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
            message = Text.translatable("msg.blind-deaf-muted.cleared").formatted(Formatting.GRAY);
        } else {
            // Translatable is resolved on each CLIENT, so a French player sees French
            // even though this runs on the server. The role name is a nested translatable
            // arg keeping its own colour/bold.
            message = Text.translatable("msg.blind-deaf-muted.now",
                            Text.translatable(role.translationKey()).formatted(role.color(), Formatting.BOLD))
                    .formatted(Formatting.WHITE);
        }
        player.sendMessage(message, false); // false = chat line, not the action bar
    }

    /** Send the player's current role to their client. */
    public void sync(ServerPlayerEntity player) {
        Role role = get(player);
        ServerPlayNetworking.send(player, new RolePayload(ModConstants.PROTOCOL_VERSION, role));
    }
}
