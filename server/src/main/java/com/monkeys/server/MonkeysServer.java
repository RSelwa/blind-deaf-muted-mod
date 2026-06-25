package com.monkeys.server;

import com.monkeys.common.ModConstants;
import com.monkeys.common.RolePayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    @Override
    public void onInitialize() {
        LOGGER.info("Monkeys server starting (protocol v{})", ModConstants.PROTOCOL_VERSION);

        // Tell the networking layer our payload exists (must also be done client-side).
        PayloadTypeRegistry.playS2C().register(RolePayload.ID, RolePayload.CODEC);

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
    }
}
