package com.blinddeafmuted.server;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Random;

/**
 * Periodic "random events" engine.
 *
 * <p>While enabled, a timer fires every {@link #MIN_INTERVAL_TICKS}..{@link #MAX_INTERVAL_TICKS}
 * (re-randomised after each fire). On each fire it picks one event from a small pool:
 * <ul>
 *   <li><b>Re-roll roles</b> — re-rolls every online player's disability via
 *       {@link RoleRoller#rollAll} (so the client roulette plays for everyone), exactly
 *       like a shattered Randomizer bottle.</li>
 *   <li><b>Random potion</b> — applies a random (mostly silly, non-lethal) status effect
 *       to one random player for {@link #POTION_DURATION_TICKS}.</li>
 * </ul>
 *
 * <p>Default OFF — opt in with {@code /bdm events on}. {@code /bdm events now} force-fires
 * one event immediately (handy for recording promos / testing) regardless of the toggle.
 *
 * <p>No new packet: re-roll reuses the role-sync/roulette path and potions use vanilla
 * status effects, so there's nothing to add to the protocol.
 */
public final class RandomEventManager {
    private final RoleManager roles;
    private final Random rng = new Random();

    /** Off by default so it never surprises a normal session until an op opts in. */
    private volatile boolean enabled = false;

    /** Random gap between events, in server ticks (20 ticks = 1 second). 3..8 minutes.
     *  Tweak freely — short for chaotic streams, long for a slow-burn challenge. */
    private static final int MIN_INTERVAL_TICKS = 3 * 60 * 20;
    private static final int MAX_INTERVAL_TICKS = 8 * 60 * 20;

    /** How long a potion event lasts (20s). */
    private static final int POTION_DURATION_TICKS = 20 * 20;

    /** Out of 100, how often a fired event is a full role re-roll vs. a potion. */
    private static final int REROLL_PERCENT = 35;

    /** Curated effect pool: funny / dramatic but non-lethal. LEVITATION/HARM left out on
     *  purpose (they can kill); BLINDNESS left out because it overlaps the blind role. */
    private static final List<RegistryEntry<StatusEffect>> POTION_EFFECTS = List.of(
            StatusEffects.SPEED,
            StatusEffects.SLOWNESS,
            StatusEffects.JUMP_BOOST,
            StatusEffects.HASTE,
            StatusEffects.NIGHT_VISION,
            StatusEffects.NAUSEA,
            StatusEffects.GLOWING,
            StatusEffects.INVISIBILITY,
            StatusEffects.REGENERATION,
            StatusEffects.WEAKNESS);

    /** Ticks remaining until the next event fires (only counts down while enabled). */
    private int ticksUntilNext;

    public RandomEventManager(RoleManager roles) {
        this.roles = roles;
        scheduleNext();
    }

    /** Hook the server tick. Inert until {@link #setEnabled} turns it on. */
    public void register() {
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Toggle the engine. Turning it on (re)arms the next-fire timer from now. */
    public void setEnabled(boolean on) {
        this.enabled = on;
        if (on) scheduleNext();
    }

    /** Pick a fresh random delay until the next event. */
    private void scheduleNext() {
        ticksUntilNext = MIN_INTERVAL_TICKS + rng.nextInt(MAX_INTERVAL_TICKS - MIN_INTERVAL_TICKS + 1);
    }

    private void tick(MinecraftServer server) {
        if (!enabled) return;
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        if (players.isEmpty()) return; // don't burn the timer with nobody online
        if (--ticksUntilNext > 0) return;
        scheduleNext();
        fire(server, players);
    }

    /**
     * Fire one random event immediately, ignoring the timer and the enabled flag.
     * Used by {@code /bdm events now} for on-demand testing / recording.
     *
     * @return false if there was no one online to affect.
     */
    public boolean fireNow(MinecraftServer server) {
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        if (players.isEmpty()) return false;
        fire(server, players);
        return true;
    }

    private void fire(MinecraftServer server, List<ServerPlayerEntity> players) {
        if (rng.nextInt(100) < REROLL_PERCENT) {
            rerollEvent(server, players);
        } else {
            potionEvent(server, players);
        }
    }

    /** Re-roll everyone's role (same path as the Randomizer bottle). */
    private void rerollEvent(MinecraftServer server, List<ServerPlayerEntity> players) {
        int count = RoleRoller.rollAll(players, roles);
        if (count > 0) {
            server.getPlayerManager().broadcast(
                    Text.literal("🎲 Random event — everyone's role was re-rolled!")
                            .formatted(Formatting.LIGHT_PURPLE),
                    false);
        }
    }

    /** Apply a random effect to one random player, and tell everyone who got what. */
    private void potionEvent(MinecraftServer server, List<ServerPlayerEntity> players) {
        ServerPlayerEntity victim = players.get(rng.nextInt(players.size()));
        RegistryEntry<StatusEffect> effect = POTION_EFFECTS.get(rng.nextInt(POTION_EFFECTS.size()));
        victim.addStatusEffect(new StatusEffectInstance(effect, POTION_DURATION_TICKS, 0));

        Text effectName = effect.value().getName();
        server.getPlayerManager().broadcast(
                Text.literal("⚗️ Random event — " + victim.getName().getString() + " got ")
                        .formatted(Formatting.AQUA)
                        .append(effectName.copy().formatted(Formatting.WHITE))
                        .append(Text.literal("!").formatted(Formatting.AQUA)),
                false);
    }
}
