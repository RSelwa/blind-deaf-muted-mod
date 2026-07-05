package com.blinddeafmuted.client;

import com.blinddeafmuted.common.ConfigPayload;
import com.blinddeafmuted.common.MegaphoneStatePayload;
import com.blinddeafmuted.common.ModConstants;
import com.blinddeafmuted.common.ModEntities;
import com.blinddeafmuted.common.Role;
import com.blinddeafmuted.common.RolePayload;
import com.blinddeafmuted.common.RollPayload;
import com.blinddeafmuted.common.RosterPayload;
import com.blinddeafmuted.common.SkinVisibilityPayload;
import com.blinddeafmuted.common.TrackerPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client entrypoint.
 *
 * <p>Listens for {@link RolePayload} from the server and updates {@link RoleState}.
 * The actual effects read that state:
 * <ul>
 *   <li>BLIND — {@link BlindOverlay} draws a black HUD layer.</li>
 *   <li>DEAF  — {@code SoundSystemMixin} scales environment sound volume down.</li>
 *   <li>MUTED — {@link MuteHandler} cancels outgoing chat.</li>
 * </ul>
 */
public class BlindDeafMutedClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("blind-deaf-muted-client");

    @Override
    public void onInitializeClient() {
        // NOTE: the shared item/entity AND the S2C payload type registrations are
        // done once in the `main` entrypoint (BlindDeafMutedServer). In this unified
        // jar `main` runs on BOTH sides and BEFORE this client entrypoint, so doing
        // them here too would double-register and crash. By the time we run, the
        // item/entity already exist — we only add the client-only renderers + receivers.

        // The thrown bottle renders as its flat item, like a splash potion / XP bottle.
        EntityRendererRegistry.register(ModEntities.RANDOMIZER_BOTTLE,
                ctx -> new FlyingItemEntityRenderer<>(ctx));

        // Add the blind cane as a feature layer on every player renderer. It decides
        // per-player (from the roster) whether to actually draw, so one registration
        // covers everyone.
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register(
                (entityType, renderer, helper, ctx) -> {
                    if (renderer instanceof PlayerEntityRenderer playerRenderer) {
                        helper.register(new BlindCaneFeatureRenderer(playerRenderer));
                        helper.register(new RoleHeadAccessoryFeatureRenderer(playerRenderer));
                        // (Megaphone mouth-cone feature renderer removed — the player holds the
                        // 3D megaphone item in hand, so the cone at the mouth was redundant.)
                    }
                });

        // The payload TYPES are registered in BlindDeafMutedServer (main, runs on
        // both sides). Here we only register the client's RECEIVERS for them.
        ClientPlayNetworking.registerGlobalReceiver(RolePayload.ID, (payload, context) -> {
            // Networking callbacks run off-thread; touch game state on the client thread.
            context.client().execute(() -> handleRole(payload));
        });

        ClientPlayNetworking.registerGlobalReceiver(TrackerPayload.ID, (payload, context) ->
                context.client().execute(() -> TrackerState.setEntries(payload.entries())));

        ClientPlayNetworking.registerGlobalReceiver(RosterPayload.ID, (payload, context) ->
                context.client().execute(() -> RosterState.setEntries(payload.entries())));

        ClientPlayNetworking.registerGlobalReceiver(SkinVisibilityPayload.ID, (payload, context) ->
                context.client().execute(() -> SkinVisibilityState.set(payload.enabled())));

        ClientPlayNetworking.registerGlobalReceiver(MegaphoneStatePayload.ID, (payload, context) ->
                context.client().execute(() -> MegaphoneState.setActive(java.util.Set.copyOf(payload.activeNames()))));

        // Live tunables: mirror the server's config so the effect mixins + slider menu read it.
        ClientPlayNetworking.registerGlobalReceiver(ConfigPayload.ID, (payload, context) ->
                context.client().execute(() -> ClientConfigState.set(payload.config())));

        // The roulette reveal: spin the slot machine, then RouletteAnimation applies
        // the role itself at the end (so we deliberately don't touch RoleState here).
        ClientPlayNetworking.registerGlobalReceiver(RollPayload.ID, (payload, context) ->
                context.client().execute(() -> RouletteAnimation.start(payload.role())));

        // Wire up the effect handlers. (BLIND's BLACKOUT_HUD draw and DEAF's muting
        // live in mixins — InGameHudMixin / SoundSystemMixin — and need no registration.)
        BlindHandler.register();  // blind-mode keybind + vanilla Blindness effect
        MyopiaController.register(); // installs the MYOPIA blur post-effect while blind
        DeafHandler.register();   // deaf muffle-intensity cycle keybind (H)
        MuteHandler.register();   // blocks outgoing chat
        TrackerHud.register();    // teammate tracker keybind (HUD draw is in InGameHudMixin)
        RosterHud.register();     // who-is-what leaderboard keybind (HUD draw is in InGameHudMixin)
        RouletteAnimation.register(); // roulette reveal countdown (HUD draw is in InGameHudMixin)
        MegaphoneController.register(); // push-to-megaphone keybind (R) → tells the server
        ConfigMenu.register();    // live-tuning slider menu keybind (O)

        LOGGER.info("Blind Deaf Muted client ready (protocol v{})", ModConstants.PROTOCOL_VERSION);
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
