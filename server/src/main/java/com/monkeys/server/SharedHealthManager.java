package com.monkeys.server;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * Optional "shared health" mode: damage taken by any one player is mirrored onto
 * every other online player, so the team feels every hit together.
 *
 * <p>This is <b>pure shared damage</b>: everyone loses the same amount, and whether
 * a given player dies is simply whether <em>their own</em> hearts ran out. A player
 * with more health survives a hit that kills a low teammate — there is no shared
 * death and no clamping; you die only when your own bar reaches zero.
 *
 * <p><b>Design (see {@code DESIGN.md}):</b> we never copy health <em>values</em>
 * between players — that loops (setting B's health re-fires B's change → set A …)
 * and fights with hunger-driven regen. Instead we listen for damage <em>events</em>
 * and re-apply the same post-armor amount once to everyone else, guarded by
 * {@link #propagating} so the mirrored hits don't cascade.
 *
 * <p><b>Healing is intentionally individual</b> for now: each player eats and
 * regenerates on their own. This is what keeps the hunger/regen loop from ever
 * firing. When we want shared healing, hook {@code LivingEntity.heal} via a mixin
 * and route it through {@link #propagateHeal} (the seam is stubbed below) — no
 * other part of this class needs to change.
 *
 * <p>Off by default; flipped live via {@code /bdm health <on|off>}. Because the
 * feature is purely server-side (health is server-authoritative; the client only
 * draws the bar), turning it off is instant and leaves no client state behind.
 */
public class SharedHealthManager {
    /** Whether shared health is currently active. Off until an op turns it on. */
    private boolean enabled = false;

    /** Re-entrancy guard: true while we're mirroring a hit, so the damage we deal to
     *  teammates doesn't itself trigger another round of mirroring. */
    private boolean propagating = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** Register the damage listener. Call once from the server entrypoint. */
    public void register() {
        ServerLivingEntityEvents.AFTER_DAMAGE.register(this::onAfterDamage);
    }

    private void onAfterDamage(LivingEntity entity, DamageSource source,
                               float baseDamageTaken, float damageTaken, boolean blocked) {
        if (!enabled || propagating) return;
        if (!(entity instanceof ServerPlayerEntity victim)) return;
        if (damageTaken <= 0.0F) return;

        MinecraftServer server = victim.getServer();
        if (server == null) return;

        // Mirror the post-armor amount to everyone else. genericKill bypasses armor and
        // i-frames, so each teammate loses exactly what the victim lost (we already paid
        // the victim's armor reduction — re-running it would double-mitigate) and the
        // hit always lands even if they were recently struck.
        //
        // No clamp: the mirrored hit subtracts from each teammate's own bar. Someone with
        // more hearts survives; someone whose own health hits zero dies — their own death,
        // not a team wipe.
        propagating = true;
        try {
            for (ServerPlayerEntity other : server.getPlayerManager().getPlayerList()) {
                if (other == victim || !other.isAlive()) continue;
                if (other.isCreative() || other.isSpectator()) continue;
                ServerWorld world = (ServerWorld) other.getWorld();
                other.damage(world, other.getDamageSources().genericKill(), damageTaken);
            }
        } finally {
            propagating = false;
        }
    }

    /**
     * Seam for future shared healing. Not wired up yet (healing is individual for now).
     * When enabling shared heal, add a mixin on {@code LivingEntity.heal(float)} that
     * calls this, mirroring {@link #onAfterDamage}: guard with {@link #propagating} and
     * apply {@code other.heal(amount)} to each teammate.
     */
    @SuppressWarnings("unused")
    private void propagateHeal(ServerPlayerEntity source, float amount) {
        // TODO: implement when we switch healing to shared.
    }
}
