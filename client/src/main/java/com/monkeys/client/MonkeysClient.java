package com.monkeys.client;

import com.monkeys.common.ModConstants;
import com.monkeys.common.ModEntities;
import com.monkeys.common.ModItems;
import com.monkeys.common.Role;
import com.monkeys.common.RolePayload;
import com.monkeys.common.RollPayload;
import com.monkeys.common.RosterPayload;
import com.monkeys.common.TrackerPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;
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
        // Register our shared item + entity (the server does the same; same ids both sides).
        ModItems.register();
        ModEntities.register();
        // The thrown bottle renders as its flat item, like a splash potion / XP bottle.
        EntityRendererRegistry.register(ModEntities.RANDOMIZER_BOTTLE,
                ctx -> new FlyingItemEntityRenderer<>(ctx));

        // Must match the server-side registration in MonkeysServer.
        PayloadTypeRegistry.playS2C().register(RolePayload.ID, RolePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TrackerPayload.ID, TrackerPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RosterPayload.ID, RosterPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RollPayload.ID, RollPayload.CODEC);

        ClientPlayNetworking.registerGlobalReceiver(RolePayload.ID, (payload, context) -> {
            // Networking callbacks run off-thread; touch game state on the client thread.
            context.client().execute(() -> handleRole(payload));
        });

        ClientPlayNetworking.registerGlobalReceiver(TrackerPayload.ID, (payload, context) ->
                context.client().execute(() -> TrackerState.setEntries(payload.entries())));

        ClientPlayNetworking.registerGlobalReceiver(RosterPayload.ID, (payload, context) ->
                context.client().execute(() -> RosterState.setEntries(payload.entries())));

        // The roulette reveal: spin the slot machine, then RouletteAnimation applies
        // the role itself at the end (so we deliberately don't touch RoleState here).
        ClientPlayNetworking.registerGlobalReceiver(RollPayload.ID, (payload, context) ->
                context.client().execute(() -> RouletteAnimation.start(payload.role())));

        // Wire up the effect handlers. (BLIND's BLACKOUT_HUD draw and DEAF's muting
        // live in mixins — InGameHudMixin / SoundSystemMixin — and need no registration.)
        BlindHandler.register();  // blind-mode keybind + vanilla Blindness effect
        DeafHandler.register();   // stops in-flight sounds on going deaf
        MuteHandler.register();   // blocks outgoing chat
        TrackerHud.register();    // teammate tracker keybind (HUD draw is in InGameHudMixin)
        RosterHud.register();     // who-is-what leaderboard keybind (HUD draw is in InGameHudMixin)
        RouletteAnimation.register(); // roulette reveal countdown (HUD draw is in InGameHudMixin)

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
