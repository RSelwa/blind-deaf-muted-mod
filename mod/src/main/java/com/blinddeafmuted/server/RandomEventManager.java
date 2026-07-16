package com.blinddeafmuted.server;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

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

    private boolean wasEnabled = false;



    /** Ticks remaining until the next re-roll (only counts down while enabled). */
    private int ticksUntilNext;

    /** Ticks remaining until the next END fast-reroll (only counts down while enabled AND a
     *  player is in the End). */
    private int endTicksUntilNext;

    public RandomEventManager(RoleManager roles, ConfigManager config) {
        this.roles = roles;
        this.config = config;
        scheduleNext();
        scheduleEndNext();
    }

    /** Hook the server tick. Inert until enabled in the config. */
    public void register() {
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
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
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        tickAutoReroll(server, players);
        tickEndReroll(server, players);
    }

    /** The always-on-anywhere timer (off by default; toggled via {@code /bdm events on}). */
    private void tickAutoReroll(MinecraftServer server, List<ServerPlayerEntity> players) {
        boolean isEnabled = config.get().eventAutoRerollEnabled() > 0.5f;

        if (isEnabled && !wasEnabled) {
            scheduleNext();
        }
        wasEnabled = isEnabled;

        if (!isEnabled) return;

        // If the user just lowered the max timer in the config, don't leave the current
        // delay stranded way above the new max.
        int currentMaxTicks = (int) (config.get().eventMaxMinutes() * 60f * 20f);
        if (ticksUntilNext > currentMaxTicks) {
            scheduleNext();
        }

        if (players.isEmpty()) return; // don't burn the timer with nobody online
        if (--ticksUntilNext > 0) return;
        scheduleNext();
        reroll(server, players);
    }

    /** The End-only fast timer: while enabled AND at least one player is in the End, reroll
     *  everyone every {@code endRerollSeconds} so roles rotate quickly through the dragon fight.
     *  The countdown resets whenever nobody is in the End, so it fires shortly after the first
     *  player arrives, not on some stale offset. */
    private void tickEndReroll(MinecraftServer server, List<ServerPlayerEntity> players) {
        if (config.get().endRerollEnabled() <= 0.5f) return;

        boolean anyInEnd = players.stream()
                .anyMatch(p -> p.getServerWorld().getRegistryKey() == World.END);
        if (!anyInEnd) {
            scheduleEndNext(); // hold the timer full while the End is empty
            return;
        }

        // Keep the current delay from stranding above a freshly-lowered slider value.
        int currentMaxTicks = Math.max(1, Math.round(config.get().endRerollSeconds() * 20f));
        if (endTicksUntilNext > currentMaxTicks) {
            scheduleEndNext();
        }

        endTicksUntilNext--;
        // 3-2-1 chat warning in the final seconds so the roll change isn't a surprise.
        if (endTicksUntilNext == 60 || endTicksUntilNext == 40 || endTicksUntilNext == 20) {
            int secs = endTicksUntilNext / 20;
            server.getPlayerManager().broadcast(
                    Text.translatable("msg.blind-deaf-muted.end_reroll_countdown", secs)
                            .formatted(Formatting.RED),
                    false);
        }
        if (endTicksUntilNext > 0) return;
        scheduleEndNext();
        reroll(server, players);
    }

    /** Fixed delay until the next End fast-reroll, read live from the config (seconds → ticks). */
    private void scheduleEndNext() {
        endTicksUntilNext = Math.max(1, Math.round(config.get().endRerollSeconds() * 20f));
    }

    /**
     * Reset the auto-reroll countdown to a fresh full interval.
     *
     * <p>Called whenever an EXTERNAL re-roll happens (a shattered Randomizer bottle) so the
     * auto-timer doesn't fire again right on its heels — e.g. throwing a bottle then getting
     * auto-switched back two minutes later. The timer restarts as if it had just fired.
     */
    public void resetTimer() {
        scheduleNext();
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
                    Text.translatable("msg.blind-deaf-muted.reroll")
                            .formatted(Formatting.RED),
                    false);
        }
    }
}
