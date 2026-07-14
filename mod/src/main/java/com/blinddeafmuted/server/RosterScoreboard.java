package com.blinddeafmuted.server;

import com.blinddeafmuted.common.Role;
import net.minecraft.scoreboard.ScoreAccess;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.scoreboard.number.BlankNumberFormat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The who-is-what roster shown through the VANILLA scoreboard sidebar (right-middle of the
 * screen) instead of a custom HUD — standard Minecraft look, and the title + role words are
 * {@link Text#translatable} so each client sees its own language (French included).
 *
 * <p>Server-owned: refreshed from {@code broadcastRoster}'s once-per-second tick. Score
 * numbers are hidden ({@link BlankNumberFormat}); each line is a per-score display text
 * ({@code Name  Role} with the role in its colour), ordered like the player list.
 *
 * <p>Anti-spoiler: while a roulette roll is animating on the clients
 * ({@link RoleManager#isRouletteRunning()}), updates are skipped so the sidebar keeps
 * showing the OLD roles until the reveal — same behaviour the old custom HUD had.
 */
public final class RosterScoreboard {

    private static final String OBJECTIVE_NAME = "bdm_roster";

    /** Names currently shown on the sidebar, so a leaver's line can be removed. */
    private final Set<String> shownNames = new HashSet<>();

    /** Rebuild the sidebar from the live player list. Call on the slow roster tick. */
    public void update(MinecraftServer server, RoleManager roleManager) {
        if (roleManager.isRouletteRunning()) return; // keep old roles until the reveal

        ServerScoreboard scoreboard = server.getScoreboard();
        ScoreboardObjective objective = scoreboard.getNullableObjective(OBJECTIVE_NAME);
        if (objective == null) {
            objective = scoreboard.addObjective(
                    OBJECTIVE_NAME,
                    ScoreboardCriterion.DUMMY,
                    Text.translatable("scoreboard.blind-deaf-muted.roster_title")
                            .formatted(Formatting.YELLOW, Formatting.BOLD),
                    ScoreboardCriterion.RenderType.INTEGER,
                    true,
                    BlankNumberFormat.INSTANCE);
        }
        scoreboard.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, objective);

        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        Set<String> current = new HashSet<>();
        for (int i = 0; i < players.size(); i++) {
            ServerPlayerEntity player = players.get(i);
            String name = player.getName().getString();
            current.add(name);
            ScoreAccess score = scoreboard.getOrCreateScore(ScoreHolder.fromName(name), objective);
            score.setScore(players.size() - i); // sidebar sorts by score desc → player-list order
            score.setDisplayText(rowText(name, roleManager.get(player)));
        }

        // Drop lines of players no longer online.
        for (String stale : shownNames) {
            if (!current.contains(stale)) {
                scoreboard.removeScore(ScoreHolder.fromName(stale), objective);
            }
        }
        shownNames.clear();
        shownNames.addAll(current);
    }

    /** One sidebar line: {@code Name  Role}, the role word translatable + in its role colour. */
    private static Text rowText(String name, Role role) {
        return Text.literal(name).formatted(Formatting.WHITE)
                .append(Text.literal("  "))
                .append(Text.translatable(role.translationKey()).formatted(role.color()));
    }
}
