package com.monkeys.client;

import com.monkeys.common.ModConstants;
import com.monkeys.common.Role;
import com.monkeys.common.RolePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client entrypoint.
 *
 * <p>Listens for {@link RolePayload} from the server and updates {@link RoleState}.
 * The actual effects read that state:
 * <ul>
 *   <li>BLIND — {@link BlindOverlay} draws a black HUD layer.</li>
 *   <li>DEAF  — {@link DeafHandler} forces sound volumes to 0.</li>
 *   <li>MUTED — {@link MuteHandler} cancels outgoing chat.</li>
 * </ul>
 */
public class MonkeysClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("monkeys-client");

    @Override
    public void onInitializeClient() {
        // Must match the server-side registration in MonkeysServer.
        PayloadTypeRegistry.playS2C().register(RolePayload.ID, RolePayload.CODEC);

        ClientPlayNetworking.registerGlobalReceiver(RolePayload.ID, (payload, context) -> {
            // Networking callbacks run off-thread; touch game state on the client thread.
            context.client().execute(() -> handleRole(payload));
        });

        // Wire up the effect handlers. (BLIND's BLACKOUT_HUD draw and DEAF's muting
        // live in mixins — InGameHudMixin / SoundSystemMixin — and need no registration.)
        BlindHandler.register();  // blind-mode keybind + vanilla Blindness effect
        DeafHandler.register();   // stops in-flight sounds on going deaf
        MuteHandler.register();   // blocks outgoing chat

        LOGGER.info("Monkeys client ready (protocol v{})", ModConstants.PROTOCOL_VERSION);
    }

    private void handleRole(RolePayload payload) {
        // Friendly version check — see DESIGN.md §2f. If the server speaks a newer
        // protocol than us, tell the player to update instead of misbehaving.
        if (payload.protocolVersion() != ModConstants.PROTOCOL_VERSION) {
            LOGGER.warn("Protocol mismatch: server v{}, client v{} — please update the client mod.",
                    payload.protocolVersion(), ModConstants.PROTOCOL_VERSION);
            // TODO: surface this to the player as an on-screen toast/message.
        }

        Role role = payload.role();
        LOGGER.info("Assigned role: {}", role);
        RoleState.set(role);
    }
}
