package com.blinddeafmuted.server;

import java.util.function.Supplier;

import com.blinddeafmuted.common.ModConfig;
import com.blinddeafmuted.common.Role;

import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * The "blind arrow crystal" rule (config {@code blindArrowCrystal}, default OFF).
 *
 * <p>When enabled, an End Crystal can only be destroyed by a <b>projectile</b> fired by a
 * <b>blind</b> player — deaf/muted/none arrows, from any bow or crossbow, normal/tipped/spectral,
 * do nothing. This forces the team to voice-guide the blind archer ("up… left… now"). Melee,
 * explosions and every other damage type are never gated — a sighted player can still whack a
 * crystal by climbing the tower and eating the blast, so the fight is never hard-locked.
 *
 * <p><b>No-blind fallback (anti-frustration):</b> the gate only bites while at least one blind
 * player is present in the crystal's world. If nobody is blind (e.g. the blind died in hardcore,
 * or a round with none assigned), crystals go fully vanilla.
 *
 * <p>Static bridge because the {@code EndCrystalEntity} mixin has no way to be handed the
 * server's {@link ConfigManager}/{@link RoleManager}; {@link BlindDeafMutedServer} binds them
 * once at startup. Both refs are {@code volatile} — the mixin runs on the server thread while
 * binding happens during init.
 */
public final class CrystalGate {
    private CrystalGate() {}

    private static volatile Supplier<ModConfig> config;
    private static volatile RoleManager roles;

    public static void bind(Supplier<ModConfig> config, RoleManager roles) {
        CrystalGate.config = config;
        CrystalGate.roles = roles;
    }

    /**
     * @return true if this End-Crystal damage should be CANCELLED (a non-blind projectile while
     *         the rule is active and a blind player is present).
     */
    public static boolean blocks(ServerWorld world, DamageSource source) {
        Supplier<ModConfig> cfg = config;
        RoleManager rm = roles;
        if (cfg == null || rm == null) return false;
        if (cfg.get().blindArrowCrystal() < 0.5f) return false;

        // Only projectiles are gated; melee / explosion / etc. pass through untouched.
        if (!(source.getSource() instanceof ProjectileEntity projectile)) return false;

        // No-blind fallback: if nobody in this world is blind, don't gate anything.
        boolean blindPresent = world.getPlayers().stream()
                .anyMatch(p -> rm.get(p.getUuid()) == Role.BLIND);
        if (!blindPresent) return false;

        Entity shooter = projectile.getOwner();
        if (shooter == null) shooter = source.getAttacker();
        boolean shooterBlind = shooter != null && rm.get(shooter.getUuid()) == Role.BLIND;
        return !shooterBlind; // block everyone but a blind shooter
    }
}
