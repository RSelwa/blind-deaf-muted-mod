package com.blinddeafmuted.server;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Random;

/**
 * Periodic auto-randomizer.
 *
 * <p>While enabled, a timer fires every {@link #MIN_INTERVAL_TICKS}..{@link #MAX_INTERVAL_TICKS}
 * (re-randomised after each fire) and re-rolls every online player's disability via
 * {@link RoleRoller#rollAll} — so the client roulette plays for everyone, exactly like a
 * shattered Randomizer bottle, but on a surprise timer instead of an item.
 *
 * <p>Default OFF — opt in with {@code /bdm events on}. {@code /bdm events now} force-fires
 * a re-roll immediately (handy for recording promos / testing) regardless of the toggle.
 *
 * <p>No new packet: re-rolls reuse the existing role-sync / roulette path.
 */
public final class RandomEventManager {
    private final RoleManager roles;
    private final Random rng = new Random();

    /** Off by default so it never surprises a normal session until an op opts in. */
    private volatile boolean enabled = false;

    /** Random gap between re-rolls, in server ticks (20 ticks = 1 second). 3..8 minutes.
     *  Tweak freely — short for chaotic streams, long for a slow-burn challenge. */
    private static final int MIN_INTERVAL_TICKS = 3 * 60 * 20;
    private static final int MAX_INTERVAL_TICKS = 8 * 60 * 20;

    /** Ticks remaining until the next re-roll (only counts down while enabled). */
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

    /** Pick a fresh random delay until the next re-roll. */
    private void scheduleNext() {
        ticksUntilNext = MIN_INTERVAL_TICKS + rng.nextInt(MAX_INTERVAL_TICKS - MIN_INTERVAL_TICKS + 1);
    }

    private void tick(MinecraftServer server) {
        if (!enabled) return;
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        if (players.isEmpty()) return; // don't burn the timer with nobody online
        if (--ticksUntilNext > 0) return;
        scheduleNext();
        reroll(server, players);
    }

    /**
     * Force a re-roll right now, ignoring the timer and the enabled flag.
     * Used by {@code /bdm events now} for on-demand testing / recording.
     *
     * @return false if there was no one online to affect.
     */
    public boolean fireNow(MinecraftServer server) {
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        if (players.isEmpty()) return false;
        reroll(server, players);
        return true;
    }

    /** Re-roll everyone's role (same path as the Randomizer bottle). */
    private void reroll(MinecraftServer server, List<ServerPlayerEntity> players) {
        int count = RoleRoller.rollAll(players, roles);
        if (count > 0) {
            server.getPlayerManager().broadcast(
                    Text.literal("🎲 Random event — everyone's role was re-rolled!")
                            .formatted(Formatting.LIGHT_PURPLE),
                    false);
        }
    }
}
