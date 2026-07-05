package com.blinddeafmuted.server;

import com.blinddeafmuted.common.CardBrandishPayload;
import com.blinddeafmuted.common.CardBrandishStatePayload;
import com.blinddeafmuted.common.CardWritePayload;
import com.blinddeafmuted.common.ConfigPayload;
import com.blinddeafmuted.common.ConfigUpdatePayload;
import com.blinddeafmuted.common.MegaphonePayload;
import com.blinddeafmuted.common.MegaphoneStatePayload;
import com.blinddeafmuted.common.ModComponents;
import com.blinddeafmuted.common.ModConstants;
import com.blinddeafmuted.common.ModEntities;
import com.blinddeafmuted.common.ModItems;
import com.blinddeafmuted.common.RandomizerBottleEntity;
import com.blinddeafmuted.common.ReliefPayload;
import com.blinddeafmuted.common.ReliefPotionEntity;
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
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.LootTables;
import net.minecraft.loot.condition.RandomChanceLootCondition;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    /** Live-tunable gameplay parameters (persisted JSON); edited from the client slider menu. */
    private final ConfigManager configManager = new ConfigManager();

    /** Optional skin-visibility mode (on by default; toggled via /bdm skin). */
    private final SkinVisibilityManager skinVisibility = new SkinVisibilityManager();

    /** Periodic random-events engine (off by default; toggled via /bdm events).
     *  Reads its re-roll interval live from {@link #configManager}. */
    private final RandomEventManager randomEvents = new RandomEventManager(roleManager, configManager);

    /** Who is currently holding the push-to-megaphone key (fed by MegaphonePayload). */
    private final MegaphoneState megaphoneState = new MegaphoneState();

    /** Who is currently brandishing a note card outward (fed by CardBrandishPayload). */
    private final CardBrandishState cardBrandishState = new CardBrandishState();

    /** Who is currently under a Potion of Relief (disability temporarily reduced). */
    private final ReliefManager reliefManager = new ReliefManager();

    /** Push teammate positions every N server ticks (20 ticks = 1s). 4/sec is smooth
     *  for a direction arrow without being chatty. */
    private static final int TRACKER_INTERVAL_TICKS = 5;
    private int tickCounter = 0;

    /** Push the who-is-what roster once per second; it changes rarely, so this is plenty
     *  fresh for joins/leaves/role-swaps without being chatty. */
    private static final int ROSTER_INTERVAL_TICKS = 20;
    private int rosterTickCounter = 0;

    /** Chest loot tables the Randomizer bottle can drop in (built-in structures).
     *  All village chest types are included (not just weaponsmith) so any village is a
     *  reliable source — most villages lack a weaponsmith. */
    private static final Set<RegistryKey<LootTable>> RANDOMIZER_CHESTS = Set.of(
            LootTables.SIMPLE_DUNGEON_CHEST,
            LootTables.ABANDONED_MINESHAFT_CHEST,
            LootTables.STRONGHOLD_CORRIDOR_CHEST,
            LootTables.JUNGLE_TEMPLE_CHEST,
            LootTables.DESERT_PYRAMID_CHEST,
            LootTables.NETHER_BRIDGE_CHEST,
            LootTables.BASTION_TREASURE_CHEST,
            LootTables.BASTION_OTHER_CHEST,
            LootTables.BASTION_BRIDGE_CHEST,
            LootTables.BASTION_HOGLIN_STABLE_CHEST,
            // Every village job-site + house chest.
            LootTables.VILLAGE_WEAPONSMITH_CHEST,
            LootTables.VILLAGE_TOOLSMITH_CHEST,
            LootTables.VILLAGE_ARMORER_CHEST,
            LootTables.VILLAGE_CARTOGRAPHER_CHEST,
            LootTables.VILLAGE_MASON_CHEST,
            LootTables.VILLAGE_SHEPARD_CHEST,
            LootTables.VILLAGE_BUTCHER_CHEST,
            LootTables.VILLAGE_FLETCHER_CHEST,
            LootTables.VILLAGE_FISHER_CHEST,
            LootTables.VILLAGE_TANNERY_CHEST,
            LootTables.VILLAGE_TEMPLE_CHEST,
            LootTables.VILLAGE_DESERT_HOUSE_CHEST,
            LootTables.VILLAGE_PLAINS_CHEST,
            LootTables.VILLAGE_TAIGA_HOUSE_CHEST,
            LootTables.VILLAGE_SNOWY_HOUSE_CHEST,
            LootTables.VILLAGE_SAVANNA_HOUSE_CHEST);

    /** How often a qualifying chest yields a Randomizer — now lives in {@link ConfigManager}
     *  ({@code randomizerChestChance}, default 0.55). Read live in the loot callback below.
     *  NOTE: loot tables only re-roll this on resource (re)load, so a live change to this one
     *  knob takes effect on the next {@code /reload} or restart, not instantly (unlike the
     *  audio/fog knobs). */

    /** Chance a single Piglin barter ALSO yields a Randomizer. A reliable, farmable
     *  source (trade gold to piglins) on top of the rare structure chests. Kept low
     *  so it's a treat, not spam — piglins barter fast. */
    private static final float PIGLIN_BARTER_CHANCE = 0.05F;

    /** Mob death drops: chance a killed mob of each type drops a Randomizer. These are
     *  pure bonuses on the entity loot tables (added, not replacing vanilla drops). */
    private static final Map<EntityType<?>, Float> MOB_DEATH_DROPS = Map.of(
            EntityType.PIGLIN, 0.10F,
            EntityType.IRON_GOLEM, 0.60F,
            EntityType.BLAZE, 0.60F);

    @Override
    public void onInitialize() {
        LOGGER.info("Blind Deaf Muted server starting (protocol v{})", ModConstants.PROTOCOL_VERSION);

        // Register our shared item + entity + data components (client does the same; same ids
        // both sides). ModComponents must exist before any note-card stack is (de)serialized.
        ModItems.register();
        ModEntities.register();
        ModComponents.register();

        // Tell the networking layer our payloads exist (must also be done client-side).
        PayloadTypeRegistry.playS2C().register(RolePayload.ID, RolePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TrackerPayload.ID, TrackerPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RosterPayload.ID, RosterPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RollPayload.ID, RollPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SkinVisibilityPayload.ID, SkinVisibilityPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MegaphoneStatePayload.ID, MegaphoneStatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ConfigPayload.ID, ConfigPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CardBrandishStatePayload.ID, CardBrandishStatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ReliefPayload.ID, ReliefPayload.CODEC);
        // Inbound: clients report their megaphone key press/release + slider-menu config edits
        // + note-card writes and brandish toggles.
        PayloadTypeRegistry.playC2S().register(MegaphonePayload.ID, MegaphonePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ConfigUpdatePayload.ID, ConfigUpdatePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(CardWritePayload.ID, CardWritePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(CardBrandishPayload.ID, CardBrandishPayload.CODEC);

        // Receive a megaphone ACTIVATION request (client sends active=true on the key press).
        // The megaphone is now a timed burst with a per-player cooldown (MegaphoneState), so a
        // press either fires a fresh burst or is refused (mid-burst / on cooldown). Everything
        // runs on the server thread: it reads the player's hands + player list and sends feedback.
        ServerPlayNetworking.registerGlobalReceiver(MegaphonePayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            if (!payload.active()) return; // releases no longer matter — the burst is time-boxed
            player.getServer().execute(() -> {
                if (!holdsMegaphoneItem(player)) return; // must actually hold a megaphone to fire
                // Burst + cooldown durations are live ModConfig knobs (slider menu).
                var cfg = configManager.get();
                long burstMs = (long) (cfg.megaphoneBurstSeconds() * 1000f);
                long cooldownMs = (long) (cfg.megaphoneCooldownSeconds() * 1000f);
                MegaphoneState.Result result =
                        megaphoneState.tryActivate(player.getUuid(), burstMs, cooldownMs);
                switch (result) {
                    case ACTIVATED -> {
                        // Vanilla hotbar cooldown overlay (the white sweep) on the megaphone item,
                        // for the whole burst+cooldown so it empties exactly when usable again. Keyed
                        // by cooldown GROUP (= item id), so it covers every megaphone the player holds
                        // — auto-synced to the client by ServerItemCooldownManager.
                        ItemStack mega = player.getMainHandStack().isOf(ModItems.MEGAPHONE)
                                ? player.getMainHandStack() : player.getOffHandStack();
                        int ticks = (int) ((burstMs + cooldownMs) / 50L);
                        player.getItemCooldownManager().set(mega, ticks);

                        player.sendMessage(Text.translatable("msg.blind-deaf-muted.megaphone_active",
                                Math.round(cfg.megaphoneBurstSeconds())).formatted(Formatting.GOLD), true);
                        broadcastMegaphoneState(player.getServer());
                    }
                    case ON_COOLDOWN -> {
                        long secs = (megaphoneState.cooldownRemainingMs(player.getUuid()) + 999L) / 1000L;
                        player.sendMessage(Text.translatable("msg.blind-deaf-muted.megaphone_cooldown", secs)
                                .formatted(Formatting.GRAY), true);
                    }
                    case ALREADY_ACTIVE -> { /* mid-burst: ignore repeat presses */ }
                }
            });
        });

        // Receive a note-card brandish toggle. Store it and re-broadcast the visual state at
        // once (so the card flips outward snappily for everyone), same as the megaphone.
        ServerPlayNetworking.registerGlobalReceiver(CardBrandishPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            cardBrandishState.set(player.getUuid(), payload.active());
            player.getServer().execute(() -> broadcastCardBrandishState(player.getServer()));
        });

        // Receive a note-card write: the client edited the card in its screen; write the text
        // onto the held stack authoritatively (the component then syncs to trackers on its own).
        // Clamp count + length defensively — never trust the client's line list.
        ServerPlayNetworking.registerGlobalReceiver(CardWritePayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            player.getServer().execute(() -> {
                ItemStack stack = player.getStackInHand(payload.hand());
                if (!stack.isOf(ModItems.NOTE_CARD)) return; // no card in that hand anymore
                List<String> lines = new ArrayList<>();
                for (String line : payload.lines()) {
                    if (lines.size() >= ModComponents.MAX_LINES) break;
                    lines.add(line.length() > ModComponents.MAX_LINE_LENGTH
                            ? line.substring(0, ModComponents.MAX_LINE_LENGTH) : line);
                }
                stack.set(ModComponents.CARD_TEXT, List.copyOf(lines));
            });
        });

        // Receive a slider-menu edit from ANY client (access = everyone, by design — the cheat
        // risk was accepted). Store + persist, apply the live side-effects, then re-broadcast
        // the authoritative config so every client (sender included) converges on it.
        ServerPlayNetworking.registerGlobalReceiver(ConfigUpdatePayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            player.getServer().execute(() -> {
                configManager.set(payload.config());
                broadcastConfig(player.getServer());
            });
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

        // When a Potion of Relief shatters, temporarily reduce the disability of every player
        // within range (the co-op boost for the dragon fight). Range + duration are live config.
        ReliefPotionEntity.SHATTER_HANDLER = bottle -> {
            MinecraftServer server = bottle.getServer();
            if (server == null || !(bottle.getWorld() instanceof ServerWorld world)) return;
            var cfg = configManager.get();
            double range = cfg.reliefRangeBlocks();
            double range2 = range * range;
            long durationMs = (long) (cfg.reliefDurationSeconds() * 1000f);
            double bx = bottle.getX(), by = bottle.getY(), bz = bottle.getZ();
            int affected = 0;
            for (ServerPlayerEntity player : world.getPlayers()) {
                if (player.squaredDistanceTo(bx, by, bz) <= range2) {
                    reliefManager.apply(player.getUuid(), durationMs);
                    player.sendMessage(Text.translatable("msg.blind-deaf-muted.relief_active",
                            Math.round(cfg.reliefDurationSeconds())).formatted(Formatting.AQUA), true);
                    affected++;
                }
            }
            if (affected > 0) broadcastReliefState(server);
        };

        // Make the Randomizer lootable in structure chests. Works on already-generated
        // worlds: a chest only rolls its loot table the first time it's opened, so any
        // unopened chest (old or new) can yield it.
        LootTableEvents.MODIFY.register((key, tableBuilder, source) -> {
            if (source.isBuiltin() && RANDOMIZER_CHESTS.contains(key)) {
                tableBuilder.pool(LootPool.builder()
                        .rolls(ConstantLootNumberProvider.create(1))
                        .conditionally(RandomChanceLootCondition.builder(
                                configManager.get().randomizerChestChance()))
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
            // Mob death drops (piglin/iron golem/blaze): add a bonus pool on each entity's
            // loot table, on top of its vanilla drops (piglin has none — pure bonus there).
            if (source.isBuiltin()) {
                for (Map.Entry<EntityType<?>, Float> drop : MOB_DEATH_DROPS.entrySet()) {
                    if (drop.getKey().getLootTableKey().map(key::equals).orElse(false)) {
                        tableBuilder.pool(LootPool.builder()
                                .rolls(ConstantLootNumberProvider.create(1))
                                .conditionally(RandomChanceLootCondition.builder(drop.getValue()))
                                .with(ItemEntry.builder(ModItems.RANDOMIZER)));
                    }
                }
            }
        });

        // Random-events timer: inert until an op runs /bdm events on.
        randomEvents.register();

        // Admin command: /bdm set <player> <blind|deaf|muted|none>, etc.
        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) ->
                BlindDeafMutedCommand.register(dispatcher, roleManager, skinVisibility, randomEvents));

        // When a player joins, immediately sync their role AND the live config (so their
        // slider menu opens on the real current values right away).
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            roleManager.sync(handler.getPlayer());
            ServerPlayNetworking.send(handler.getPlayer(), new ConfigPayload(configManager.get()));
        });

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
            BlindDeafMutedVoicechatPlugin.bindRelief(reliefManager);
            // VoiceFx reads the live audio tunables off this supplier each frame.
            BlindDeafMutedVoicechatPlugin.bindConfig(configManager::get);
        }

        // Drop a leaver's megaphone + card-brandish flags so a disconnect can't leave them stuck.
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            megaphoneState.clear(handler.getPlayer().getUuid());
            cardBrandishState.clear(handler.getPlayer().getUuid());
            reliefManager.clear(handler.getPlayer().getUuid());
        });

        // A few times per second, send every player the positions of all the others,
        // so their client can draw the teammate tracker HUD. The client decides
        // whether to show it (toggle + never while blind).
        ServerTickEvents.END_SERVER_TICK.register(this::broadcastTrackerPositions);

        // Once per second, send everyone the full who-is-what roster for the
        // leaderboard HUD. The list is identical for every recipient, so it's built
        // once and sent to all.
        ServerTickEvents.END_SERVER_TICK.register(this::broadcastRoster);
    }

    /** Push the live config to every online player (after a slider edit). */
    private void broadcastConfig(net.minecraft.server.MinecraftServer server) {
        ConfigPayload payload = new ConfigPayload(configManager.get());
        for (ServerPlayerEntity recipient : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(recipient, payload);
        }
    }

    /** Send each online player a {@link TrackerPayload} of every other player's position. */
    private void broadcastTrackerPositions(net.minecraft.server.MinecraftServer server) {
        if (++tickCounter < TRACKER_INTERVAL_TICKS) return;
        tickCounter = 0;

        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        if (players.isEmpty()) return; // no one online

        // Build ONE list of every online player (self included) and send the same payload
        // to everyone; the client filters itself out by name. Rebuilt from the live player
        // list each tick, so a teammate who disconnects, reconnects, or changes dimension
        // never lingers stale on anyone's HUD (and a lone player just sees an empty tracker).
        List<TrackerPayload.Entry> entries = new ArrayList<>(players.size());
        for (ServerPlayerEntity player : players) {
            entries.add(new TrackerPayload.Entry(
                    player.getName().getString(),
                    player.getX(), player.getY(), player.getZ(),
                    player.getWorld().getRegistryKey().getValue().toString()));
        }
        TrackerPayload payload = new TrackerPayload(entries);
        for (ServerPlayerEntity recipient : players) {
            ServerPlayNetworking.send(recipient, payload);
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

        // Megaphone + card-brandish states ride the same slow tick (keeps late-joiners synced);
        // both are also pushed immediately on each transition for snappiness.
        broadcastMegaphoneState(server);
        broadcastCardBrandishState(server);
        broadcastReliefState(server);
    }

    /** Broadcast the names of everyone currently under a Potion of Relief, so each client can
     *  scale down its own disability effects. Built from online players (no stale uuids). */
    private void broadcastReliefState(net.minecraft.server.MinecraftServer server) {
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        if (players.isEmpty()) return;

        List<String> relieved = new ArrayList<>();
        for (ServerPlayerEntity player : players) {
            if (reliefManager.isActive(player.getUuid())) {
                relieved.add(player.getName().getString());
            }
        }
        ReliefPayload payload = new ReliefPayload(relieved);
        for (ServerPlayerEntity recipient : players) {
            ServerPlayNetworking.send(recipient, payload);
        }
    }

    /** Broadcast the names of everyone currently brandishing a note card, so every client can
     *  flip the card FACE outward on them. Built from online players, so a leaver never lingers. */
    private void broadcastCardBrandishState(net.minecraft.server.MinecraftServer server) {
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        if (players.isEmpty()) return;

        List<String> brandishing = new ArrayList<>();
        for (ServerPlayerEntity player : players) {
            if (cardBrandishState.isActive(player.getUuid())) {
                brandishing.add(player.getName().getString());
            }
        }
        CardBrandishStatePayload payload = new CardBrandishStatePayload(brandishing);
        for (ServerPlayerEntity recipient : players) {
            ServerPlayNetworking.send(recipient, payload);
        }
    }

    /** Broadcast the names of everyone currently megaphoning, so every client can draw the
     *  megaphone-at-the-mouth model on them. Built from the online players, so a stale uuid
     *  (leaver) never shows up. */
    private void broadcastMegaphoneState(net.minecraft.server.MinecraftServer server) {
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        if (players.isEmpty()) return;

        List<String> megaphoning = new ArrayList<>();
        for (ServerPlayerEntity player : players) {
            // Visual matches the actual burst window (the roster tick refreshes it within ~1s of
            // the 5s burst ending; the audio itself stops exactly on time via isActive()).
            if (megaphoneState.isActive(player.getUuid())) {
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
