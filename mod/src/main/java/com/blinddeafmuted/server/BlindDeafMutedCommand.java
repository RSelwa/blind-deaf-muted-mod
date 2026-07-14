package com.blinddeafmuted.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.blinddeafmuted.common.ModItems;
import com.blinddeafmuted.common.Role;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;
import net.minecraft.command.argument.EntityArgumentType;

import java.util.ArrayList;
import java.util.List;

/**
 * The admin command tree. Requires op (permission level 2).
 *
 * <p>Usage so far:
 * <pre>
 *   /bdm set &lt;player&gt; &lt;blind|deaf|muted|none&gt;   assign one player
 *   /bdm random                                    random disability to everyone
 *   /bdm clear                                     reset everyone to NONE
 *   /bdm status                                    list everyone's current role
 *   /bdm help                                      show the command reference
 * </pre>
 * TODO (later): team management, random-event triggers, and an assignment
 * animation in front of {@code random}.
 */
public final class BlindDeafMutedCommand {
    private BlindDeafMutedCommand() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                RoleManager roles, SkinVisibilityManager skinVisibility,
                                RandomEventManager randomEvents) {
        // One <target> argument node, with a literal per role hung off it so
        // tab-completion offers /bdm set <player> blind|deaf|muted|none.
        var target = argument("target", EntityArgumentType.player());
        for (Role role : Role.values()) {
            target = target.then(
                    literal(role.name().toLowerCase())
                            .executes(ctx -> apply(ctx, roles, role)));
        }

        dispatcher.register(
                literal("bdm")
                        .requires(src -> src.hasPermissionLevel(2))
                        .then(literal("set").then(target))
                        .then(literal("random").executes(ctx -> randomize(ctx, roles)))
                        .then(literal("clear").executes(ctx -> clear(ctx, roles)))
                        .then(literal("status").executes(ctx -> status(ctx, roles)))
                        .then(literal("randomizer").executes(BlindDeafMutedCommand::giveRandomizer))
                        .then(literal("megaphone").executes(BlindDeafMutedCommand::giveMegaphone))
                        .then(literal("cane").executes(BlindDeafMutedCommand::giveCane))
                        .then(literal("card").executes(BlindDeafMutedCommand::giveNoteCard))
                        .then(literal("help").executes(BlindDeafMutedCommand::help))
                        .then(literal("skin")
                                .then(literal("on").executes(ctx -> setSkins(ctx, skinVisibility, true)))
                                .then(literal("off").executes(ctx -> setSkins(ctx, skinVisibility, false)))
                                .executes(ctx -> skinsStatus(ctx, skinVisibility)))
                        .then(literal("events")
                                .then(literal("now").executes(ctx -> fireEventNow(ctx, randomEvents))))
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

        // setAnimated (inside RoleRoller): each client plays the roulette reveal and
        // applies the effect at the end, instead of snapping to the new role instantly.
        final int count = RoleRoller.rollAll(players, roles);
        ctx.getSource().sendFeedback(
                () -> Text.literal("Assigned random disabilities to " + count + " player(s)."),
                true);
        return count;
    }

    /**
     * Give the command runner a few Randomizer bottles — a test/spawn helper so you
     * don't have to loot a fresh chest just to try the re-roll item.
     */
    private static int giveRandomizer(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendError(Text.literal("Run this as a player."));
            return 0;
        }
        player.giveItemStack(new ItemStack(ModItems.RANDOMIZER, 4));
        ctx.getSource().sendFeedback(() -> Text.literal("Gave 4 Randomizer bottles."), false);
        return 1;
    }

    /**
     * Give the command runner a Megaphone — hold it while talking so a DEAF teammate
     * hears your voice loud and saturated instead of near-silent.
     */
    private static int giveMegaphone(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendError(Text.literal("Run this as a player."));
            return 0;
        }
        player.giveItemStack(new ItemStack(ModItems.MEGAPHONE, 1));
        ctx.getSource().sendFeedback(() -> Text.literal("Gave 1 Megaphone."), false);
        return 1;
    }

    /**
     * Give the command runner a Cane — a BLIND player holding it upgrades their full
     * blackout to the reduced "see your feet" fog.
     */
    private static int giveCane(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendError(Text.literal("Run this as a player."));
            return 0;
        }
        player.giveItemStack(new ItemStack(ModItems.CANE, 1));
        ctx.getSource().sendFeedback(() -> Text.literal("Gave 1 Cane."), false);
        return 1;
    }

    /**
     * Give the command runner a Note Card — the MUTED player's writing tool. Press the
     * write key while holding it to edit (≤6 lines), right-click to brandish it to teammates.
     */
    private static int giveNoteCard(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendError(Text.literal("Run this as a player."));
            return 0;
        }
        player.giveItemStack(new ItemStack(ModItems.NOTE_CARD, 1));
        ctx.getSource().sendFeedback(() -> Text.literal("Gave 1 Note Card."), false);
        return 1;
    }

    /** Turn skin-visibility mode on or off. */
    private static int setSkins(CommandContext<ServerCommandSource> ctx,
                                SkinVisibilityManager skinVisibility, boolean on) {
        skinVisibility.setSkinsEnabled(on);
        ctx.getSource().sendFeedback(
                () -> Text.literal("Skins are now " + (on ? "ON" : "OFF") + ".")
                        .formatted(on ? Formatting.GREEN : Formatting.GRAY),
                true);
        return 1;
    }

    /** Report whether Skins modes is currently on. */
    private static int skinsStatus(CommandContext<ServerCommandSource> ctx,
                                    SkinVisibilityManager skinVisibility) {
        boolean on = skinVisibility.isEnabled();
        ctx.getSource().sendFeedback(
                () -> Text.literal("Skins are " + (on ? "ON" : "OFF") + "."),
                false);
        return 1;
    }



    /** Force-fire one random event right now (ignores the toggle) — for testing / recording. */
    private static int fireEventNow(CommandContext<ServerCommandSource> ctx,
                                    RandomEventManager randomEvents) {
        boolean fired = randomEvents.fireNow(ctx.getSource().getServer());
        if (!fired) {
            ctx.getSource().sendError(Text.literal("No players online to affect."));
            return 0;
        }
        return 1;
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

        StringBuilder sb = new StringBuilder("Blind Deaf Muted roles (" + players.size() + " online):");
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
                Blind Deaf Muted commands:
                  /bdm set <player> <blind|deaf|muted|none>  - assign one player a role
                  /bdm random                                - random disability to every online player
                  /bdm clear                                 - reset everyone to NONE
                  /bdm status                                - list every player's current role
                  /bdm randomizer                            - give yourself Randomizer bottles (test)
                  /bdm megaphone                             - give yourself a Megaphone (talk loud/saturated to deaf players)
                  /bdm cane                                  - give yourself a Cane (blind: hold it to ease harsh myopia to the soft version)
                  /bdm card                                  - give yourself a Note Card (muted: write on it, right-click to show teammates)
                  /bdm skin <on|off>                         - toggle the custom role accessories (cane/glasses/bandage/headset)
                  /bdm events <on|off>                       - toggle the periodic random-events timer (re-roll / random potion)
                  /bdm events now                            - force-fire one random event now (testing/recording)
                  /bdm help                                  - show this help
                Random assignment gives every disability out once before any repeats.""";
        ctx.getSource().sendFeedback(() -> Text.literal(text), false);
        return 1;
    }
}
