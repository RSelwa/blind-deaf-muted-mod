package com.monkeys.server;

import com.monkeys.common.ModConstants;
import com.monkeys.common.RolePayload;
import com.monkeys.common.TrackerPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Server entrypoint.
 *
 * <p>Responsibilities:
 * <ol>
 *   <li>Register the {@link RolePayload} so we can send roles to clients.</li>
 *   <li>Register the {@code /monkeys} admin command (assign/move players).</li>
 *   <li>Push the current role to a player when they join.</li>
 * </ol>
 * Random events come later and will hook in here too.
 */
public class MonkeysServer implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("monkeys-server");

    /** In-memory role store. TODO: persist to world save so roles survive restarts. */
    private final RoleManager roleManager = new RoleManager();

    /** Push teammate positions every N server ticks (20 ticks = 1s). 4/sec is smooth
     *  for a direction arrow without being chatty. */
    private static final int TRACKER_INTERVAL_TICKS = 5;
    private int tickCounter = 0;

    @Override
    public void onInitialize() {
        LOGGER.info("Monkeys server starting (protocol v{})", ModConstants.PROTOCOL_VERSION);

        // Tell the networking layer our payloads exist (must also be done client-side).
        PayloadTypeRegistry.playS2C().register(RolePayload.ID, RolePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TrackerPayload.ID, TrackerPayload.CODEC);

        // Admin command: /monkeys set <player> <blind|deaf|muted|none>, etc.
        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) ->
                MonkeysCommand.register(dispatcher, roleManager));

        // When a player joins, immediately sync whatever role they have.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                roleManager.sync(handler.getPlayer()));

        // Hand the role store to the Simple Voice Chat integration. Safe even if
        // that mod isn't installed: the plugin class is only loaded via the
        // `voicechat` entrypoint (read solely by the voice-chat mod), and this
        // call only sets a static reference — it touches no voice-chat classes.
        MonkeysVoicechatPlugin.bind(roleManager);

        // A few times per second, send every player the positions of all the others,
        // so their client can draw the teammate tracker HUD. The client decides
        // whether to show it (toggle + never while blind).
        ServerTickEvents.END_SERVER_TICK.register(this::broadcastTrackerPositions);
    }

    /** Send each online player a {@link TrackerPayload} of every other player's position. */
    private void broadcastTrackerPositions(net.minecraft.server.MinecraftServer server) {
        if (++tickCounter < TRACKER_INTERVAL_TICKS) return;
        tickCounter = 0;

        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        if (players.size() < 2) return; // nobody to track

        for (ServerPlayerEntity recipient : players) {
            List<TrackerPayload.Entry> entries = new ArrayList<>(players.size() - 1);
            for (ServerPlayerEntity other : players) {
                if (other == recipient) continue;
                entries.add(new TrackerPayload.Entry(
                        other.getName().getString(),
                        other.getX(), other.getY(), other.getZ()));
            }
            ServerPlayNetworking.send(recipient, new TrackerPayload(entries));
        }
    }
}
