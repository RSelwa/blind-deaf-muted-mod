package com.monkeys.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.monkeys.common.Role;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;
import net.minecraft.command.argument.EntityArgumentType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The admin command tree. Requires op (permission level 2).
 *
 * <p>Usage so far:
 * <pre>
 *   /monkeys set &lt;player&gt; &lt;blind|deaf|muted|none&gt;   assign one player
 *   /monkeys random                                    random disability to everyone
 *   /monkeys clear                                     reset everyone to NONE
 *   /monkeys status                                    list everyone's current role
 *   /monkeys help                                      show the command reference
 * </pre>
 * TODO (later): team management, random-event triggers, and an assignment
 * animation in front of {@code random}.
 */
public final class MonkeysCommand {
    private MonkeysCommand() {}

    /** Shared RNG for assignment. Not security-sensitive; default seed is fine. */
    private static final Random RNG = new Random();

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                RoleManager roles) {
        // One <target> argument node, with a literal per role hung off it so
        // tab-completion offers /monkeys set <player> blind|deaf|muted|none.
        var target = argument("target", EntityArgumentType.player());
        for (Role role : Role.values()) {
            target = target.then(
                    literal(role.name().toLowerCase())
                            .executes(ctx -> apply(ctx, roles, role)));
        }

        dispatcher.register(
                literal("monkeys")
                        .requires(src -> src.hasPermissionLevel(2))
                        .then(literal("set").then(target))
                        .then(literal("random").executes(ctx -> randomize(ctx, roles)))
                        .then(literal("clear").executes(ctx -> clear(ctx, roles)))
                        .then(literal("status").executes(ctx -> status(ctx, roles)))
                        .then(literal("help").executes(MonkeysCommand::help))
        );
    }

    private static int apply(CommandContext<ServerCommandSource> ctx,
                             RoleManager roles, Role role) {
        try {
            ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "target");
            roles.set(target, role);
            ctx.getSource().sendFeedback(
                    () -> Text.literal("Set " + target.getName().getString() + " -> ")
                            .append(Text.literal(role.name()).formatted(role.color())),
                    true);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("Failed: " + e.getMessage()));
            return 0;
        }
    }

    /**
     * Assign a random disability to every online player.
     *
     * <p>Fairness rule (per design): every disability in {@link Role#ASSIGNABLE} is
     * handed out at least once before any is duplicated. So with 3 players and 3
     * disabilities, all three differ; with more players, duplicates appear only
     * after the full set is covered (e.g. 4 players → one disability shared by two).
     *
     * <p>Implementation: shuffle the disability pool once (randomizes <em>which</em>
     * disabilities fill the distinct slots and which get duplicated), shuffle the
     * players (randomizes <em>who</em> gets what), then deal the shuffled pool out
     * cyclically. Cycling guarantees full coverage before repeats.
     */
    private static int randomize(CommandContext<ServerCommandSource> ctx, RoleManager roles) {
        List<ServerPlayerEntity> players =
                new ArrayList<>(ctx.getSource().getServer().getPlayerManager().getPlayerList());
        if (players.isEmpty()) {
            ctx.getSource().sendError(Text.literal("No players online to assign."));
            return 0;
        }

        List<Role> pool = new ArrayList<>(Arrays.asList(Role.ASSIGNABLE));
        Collections.shuffle(pool, RNG);
        Collections.shuffle(players, RNG);

        int poolSize = Role.ASSIGNABLE.length;
        for (int i = 0; i < players.size(); i++) {
            // setAnimated: each client plays the roulette reveal and applies the effect
            // at the end, instead of snapping to the new role instantly.
            roles.setAnimated(players.get(i), pool.get(i % poolSize));
        }

        final int count = players.size();
        ctx.getSource().sendFeedback(
                () -> Text.literal("Assigned random disabilities to " + count + " player(s)."),
                true);
        return count;
    }

    /** Reset every online player back to {@link Role#NONE}. */
    private static int clear(CommandContext<ServerCommandSource> ctx, RoleManager roles) {
        List<ServerPlayerEntity> players =
                ctx.getSource().getServer().getPlayerManager().getPlayerList();
        for (ServerPlayerEntity player : players) {
            roles.set(player, Role.NONE);
        }
        final int count = players.size();
        ctx.getSource().sendFeedback(
                () -> Text.literal("Cleared disabilities for " + count + " player(s)."),
                true);
        return count;
    }

    /**
     * List every online player and their current role. Handy for verifying a
     * {@code random} roll without relying on the visual/audio effects — useful
     * when testing solo.
     */
    private static int status(CommandContext<ServerCommandSource> ctx, RoleManager roles) {
        List<ServerPlayerEntity> players =
                ctx.getSource().getServer().getPlayerManager().getPlayerList();
        if (players.isEmpty()) {
            ctx.getSource().sendFeedback(() -> Text.literal("No players online."), false);
            return 0;
        }

        StringBuilder sb = new StringBuilder("Monkeys roles (" + players.size() + " online):");
        for (ServerPlayerEntity player : players) {
            sb.append("\n  ")
              .append(player.getName().getString())
              .append(" -> ")
              .append(roles.get(player).name());
        }
        // Status is a query, so don't broadcast to other ops (sendFeedback false).
        ctx.getSource().sendFeedback(() -> Text.literal(sb.toString()), false);
        return players.size();
    }

    /** Print the command reference. Available to anyone with op (the whole tree is op-only). */
    private static int help(CommandContext<ServerCommandSource> ctx) {
        String text = """
                Monkeys commands:
                  /monkeys set <player> <blind|deaf|muted|none>  - assign one player a role
                  /monkeys random                                - random disability to every online player
                  /monkeys clear                                 - reset everyone to NONE
                  /monkeys status                                - list every player's current role
                  /monkeys help                                  - show this help
                Random assignment gives every disability out once before any repeats.""";
        ctx.getSource().sendFeedback(() -> Text.literal(text), false);
        return 1;
    }
}
