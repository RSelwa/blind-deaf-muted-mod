package com.blinddeafmuted.server;

import com.blinddeafmuted.common.MegaphonePayload;
import com.blinddeafmuted.common.MegaphoneStatePayload;
import com.blinddeafmuted.common.ModConstants;
import com.blinddeafmuted.common.ModEntities;
import com.blinddeafmuted.common.ModItems;
import com.blinddeafmuted.common.RandomizerBottleEntity;
import com.blinddeafmuted.common.RolePayload;
import com.blinddeafmuted.common.RollPayload;
import com.blinddeafmuted.common.RosterPayload;
import com.blinddeafmuted.common.SkinVisibilityPayload;
import com.blinddeafmuted.common.TrackerPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.LootTables;
import net.minecraft.loot.condition.RandomChanceLootCondition;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Server entrypoint.
 *
 * <p>Responsibilities:
 * <ol>
 *   <li>Register the {@link RolePayload} so we can send roles to clients.</li>
 *   <li>Register the {@code /bdm} admin command (assign/move players).</li>
 *   <li>Push the current role to a player when they join.</li>
 * </ol>
 * Random events come later and will hook in here too.
 */
public class BlindDeafMutedServer implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("blind-deaf-muted-server");

    /** In-memory role store. TODO: persist to world save so roles survive restarts. */
    private final RoleManager roleManager = new RoleManager();

    /** Optional skin-visibility mode (on by default; toggled via /bdm skin). */
    private final SkinVisibilityManager skinVisibility = new SkinVisibilityManager();

    /** Periodic random-events engine (off by default; toggled via /bdm events). */
    private final RandomEventManager randomEvents = new RandomEventManager(roleManager);

    /** Who is currently holding the push-to-megaphone key (fed by MegaphonePayload). */
    private final MegaphoneState megaphoneState = new MegaphoneState();

    /** Push teammate positions every N server ticks (20 ticks = 1s). 4/sec is smooth
     *  for a direction arrow without being chatty. */
    private static final int TRACKER_INTERVAL_TICKS = 5;
    private int tickCounter = 0;

    /** Push the who-is-what roster once per second; it changes rarely, so this is plenty
     *  fresh for joins/leaves/role-swaps without being chatty. */
    private static final int ROSTER_INTERVAL_TICKS = 20;
    private int rosterTickCounter = 0;

    /** Chest loot tables the Randomizer bottle can drop in (built-in structures). */
    private static final Set<RegistryKey<LootTable>> RANDOMIZER_CHESTS = Set.of(
            LootTables.SIMPLE_DUNGEON_CHEST,
            LootTables.ABANDONED_MINESHAFT_CHEST,
            LootTables.VILLAGE_WEAPONSMITH_CHEST,
            LootTables.STRONGHOLD_CORRIDOR_CHEST,
            LootTables.JUNGLE_TEMPLE_CHEST,
            LootTables.DESERT_PYRAMID_CHEST,
            LootTables.NETHER_BRIDGE_CHEST,
            LootTables.BASTION_TREASURE_CHEST);

    /** Roughly how often a qualifying chest yields a Randomizer (0.15 = 15%). Bumped
     *  from 0.10 because the rarest source (village weaponsmith — not every village
     *  has one, single chest) made it almost never show up. */
    private static final float RANDOMIZER_CHANCE = 0.15F;

    /** Chance a single Piglin barter ALSO yields a Randomizer. A reliable, farmable
     *  source (trade gold to piglins) on top of the rare structure chests. Kept low
     *  so it's a treat, not spam — piglins barter fast. */
    private static final float PIGLIN_BARTER_CHANCE = 0.05F;

    @Override
    public void onInitialize() {
        LOGGER.info("Blind Deaf Muted server starting (protocol v{})", ModConstants.PROTOCOL_VERSION);

        // Register our shared item + entity (the client does the same; same ids both sides).
        ModItems.register();
        ModEntities.register();

        // Tell the networking layer our payloads exist (must also be done client-side).
        PayloadTypeRegistry.playS2C().register(RolePayload.ID, RolePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TrackerPayload.ID, TrackerPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RosterPayload.ID, RosterPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RollPayload.ID, RollPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SkinVisibilityPayload.ID, SkinVisibilityPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MegaphoneStatePayload.ID, MegaphoneStatePayload.CODEC);
        // Inbound: clients report their megaphone key press/release.
        PayloadTypeRegistry.playC2S().register(MegaphonePayload.ID, MegaphonePayload.CODEC);

        // Receive megaphone key transitions. The concurrent set mutation is thread-safe,
        // but rebroadcasting the visual state touches the player list, so hop to the
        // server thread for that. Broadcasting on each transition (not just the slow
        // roster tick) keeps the mouth-model animation snappy on press/release.
        ServerPlayNetworking.registerGlobalReceiver(MegaphonePayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            megaphoneState.set(player.getUuid(), payload.active());
            player.getServer().execute(() -> broadcastMegaphoneState(player.getServer()));
        });

        // When a Randomizer bottle shatters, re-roll EVERY online player's role.
        RandomizerBottleEntity.SHATTER_HANDLER = bottle -> {
            MinecraftServer server = bottle.getServer();
            if (server == null) return;
            List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
            int count = RoleRoller.rollAll(players, roleManager);
            if (count > 0) {
                server.getPlayerManager().broadcast(
                        Text.literal("🎲 A Randomizer shattered — everyone's role was re-rolled!")
                                .formatted(Formatting.LIGHT_PURPLE),
                        false);
            }
        };

        // Make the Randomizer lootable in structure chests. Works on already-generated
        // worlds: a chest only rolls its loot table the first time it's opened, so any
        // unopened chest (old or new) can yield it.
        LootTableEvents.MODIFY.register((key, tableBuilder, source) -> {
            if (source.isBuiltin() && RANDOMIZER_CHESTS.contains(key)) {
                tableBuilder.pool(LootPool.builder()
                        .rolls(ConstantLootNumberProvider.create(1))
                        .conditionally(RandomChanceLootCondition.builder(RANDOMIZER_CHANCE))
                        .with(ItemEntry.builder(ModItems.RANDOMIZER)));
            }
            // Piglin bartering: a reliable Nether source. Adds an independent pool that
            // rolls once per barter at PIGLIN_BARTER_CHANCE, on top of the normal barter.
            if (source.isBuiltin() && key.equals(LootTables.PIGLIN_BARTERING_GAMEPLAY)) {
                tableBuilder.pool(LootPool.builder()
                        .rolls(ConstantLootNumberProvider.create(1))
                        .conditionally(RandomChanceLootCondition.builder(PIGLIN_BARTER_CHANCE))
                        .with(ItemEntry.builder(ModItems.RANDOMIZER)));
            }
        });

        // Random-events timer: inert until an op runs /bdm events on.
        randomEvents.register();

        // Admin command: /bdm set <player> <blind|deaf|muted|none>, etc.
        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) ->
                BlindDeafMutedCommand.register(dispatcher, roleManager, skinVisibility, randomEvents));

        // When a player joins, immediately sync whatever role they have.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                roleManager.sync(handler.getPlayer()));

        // Hand the role store to the Simple Voice Chat integration — but ONLY if
        // the voice-chat mod is actually installed. SVC is an optional soft
        // dependency. Touching BlindDeafMutedVoicechatPlugin (even a static call)
        // forces the JVM to load+link that class, and because it
        // `implements VoicechatPlugin`, linking needs the SVC API on the runtime
        // classpath. Without SVC that's a NoClassDefFoundError that crashes the
        // whole `main` entrypoint. The isModLoaded gate keeps the plugin class
        // untouched (so never loaded) when SVC is absent. The `voicechat`
        // entrypoint itself is still only invoked by SVC when present.
        if (FabricLoader.getInstance().isModLoaded("voicechat")) {
            BlindDeafMutedVoicechatPlugin.bind(roleManager);
            BlindDeafMutedVoicechatPlugin.bindMegaphone(megaphoneState);
        }

        // Drop a leaver's megaphone flag so a disconnect mid-press can't leave it stuck on.
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                megaphoneState.clear(handler.getPlayer().getUuid()));

        // A few times per second, send every player the positions of all the others,
        // so their client can draw the teammate tracker HUD. The client decides
        // whether to show it (toggle + never while blind).
        ServerTickEvents.END_SERVER_TICK.register(this::broadcastTrackerPositions);

        // Once per second, send everyone the full who-is-what roster for the
        // leaderboard HUD. The list is identical for every recipient, so it's built
        // once and sent to all.
        ServerTickEvents.END_SERVER_TICK.register(this::broadcastRoster);
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

    /** Build the who-is-what roster once and broadcast it to every online player. */
    private void broadcastRoster(net.minecraft.server.MinecraftServer server) {
        if (++rosterTickCounter < ROSTER_INTERVAL_TICKS) return;
        rosterTickCounter = 0;

        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        if (players.isEmpty()) return;

        List<RosterPayload.Entry> entries = new ArrayList<>(players.size());
        for (ServerPlayerEntity player : players) {
            entries.add(new RosterPayload.Entry(
                    player.getName().getString(), roleManager.get(player)));
        }

        RosterPayload payload = new RosterPayload(entries);
        // The skin-visibility flag is the same for everyone, so it rides along on the
        // same slow tick as the roster (cheap, and keeps a late-joiner / re-login in sync
        // within a second of connecting).
        SkinVisibilityPayload skinPayload = new SkinVisibilityPayload(skinVisibility.isEnabled());

        for (ServerPlayerEntity recipient : players) {
            ServerPlayNetworking.send(recipient, payload);
            ServerPlayNetworking.send(recipient, skinPayload);
        }

        // Megaphone state rides the same slow tick (keeps late-joiners synced); it's also
        // pushed immediately on each press/release for snappiness.
        broadcastMegaphoneState(server);
    }

    /** Broadcast the names of everyone currently megaphoning, so every client can draw the
     *  megaphone-at-the-mouth model on them. Built from the online players, so a stale uuid
     *  (leaver) never shows up. */
    private void broadcastMegaphoneState(net.minecraft.server.MinecraftServer server) {
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        if (players.isEmpty()) return;

        List<String> megaphoning = new ArrayList<>();
        for (ServerPlayerEntity player : players) {
            // Visual triggers on either path: holding the megaphone key (R) OR the item.
            if (megaphoneState.isActive(player.getUuid()) || holdsMegaphoneItem(player)) {
                megaphoning.add(player.getName().getString());
            }
        }
        MegaphoneStatePayload megaphonePayload = new MegaphoneStatePayload(megaphoning);
        for (ServerPlayerEntity recipient : players) {
            ServerPlayNetworking.send(recipient, megaphonePayload);
        }
    }

    /** Whether the player is holding the megaphone item in either hand (drives the
     *  arm-pose + horn visual, same as the push-to-megaphone key). */
    private static boolean holdsMegaphoneItem(ServerPlayerEntity player) {
        return player.getMainHandStack().isOf(ModItems.MEGAPHONE)
                || player.getOffHandStack().isOf(ModItems.MEGAPHONE);
    }
}
