package com.monkeys.server;

import com.mojang.brigadier.CommandDispatcher;
import com.monkeys.common.Role;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;
import net.minecraft.command.argument.EntityArgumentType;

/**
 * The admin command tree. Requires op (permission level 2).
 *
 * <p>Usage so far:
 * <pre>
 *   /monkeys set &lt;player&gt; &lt;blind|deaf|muted|none&gt;
 * </pre>
 * TODO (later): team management, random-event triggers, list/status subcommands.
 */
public final class MonkeysCommand {
    private MonkeysCommand() {}

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
        );
    }

    private static int apply(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx,
                             RoleManager roles, Role role) {
        try {
            ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "target");
            roles.set(target, role);
            ctx.getSource().sendFeedback(
                    () -> Text.literal("Set " + target.getName().getString() + " -> " + role.name()),
                    true);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("Failed: " + e.getMessage()));
            return 0;
        }
    }
}
