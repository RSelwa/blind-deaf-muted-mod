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
    private final ConfigManager config;
    private final Random rng = new Random();

    /** Off by default so it never surprises a normal session until an op opts in. */
    private volatile boolean enabled = false;

    /** Ticks remaining until the next re-roll (only counts down while enabled). */
    private int ticksUntilNext;

    public RandomEventManager(RoleManager roles, ConfigManager config) {
        this.roles = roles;
        this.config = config;
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

    /** Pick a fresh random delay until the next re-roll, reading the min/max interval live from
     *  the config (minutes → ticks; 20 ticks = 1 s). A slider change applies from the next
     *  scheduled fire onward. Guards against min > max and a zero/negative window. */
    private void scheduleNext() {
        int minTicks = Math.max(1, Math.round(config.get().eventMinMinutes() * 60f * 20f));
        int maxTicks = Math.max(minTicks, Math.round(config.get().eventMaxMinutes() * 60f * 20f));
        ticksUntilNext = minTicks + rng.nextInt(maxTicks - minTicks + 1);
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
