package com.blinddeafmuted.server;

import com.blinddeafmuted.common.Role;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Shared random-role assignment, used by both {@code /bdm random} and the
 * Randomizer bottle so they roll identically (and both trigger the client roulette
 * via {@link RoleManager#setAnimated}).
 *
 * <p>Fairness rule (per design): every disability in {@link Role#ASSIGNABLE} is
 * handed out at least once before any repeats. Each candidate deal shuffles the
 * pool (randomises which disabilities fill the distinct slots and which duplicate)
 * and deals it out cyclically — cycling guarantees full coverage before repeats.
 *
 * <p>History rule (anti-streak, per the user: "I was often blind"): the roller
 * tries {@link #DEAL_TRIALS} candidate deals and keeps the one whose roles the
 * players have had LEAST overall ({@link RoleHistory} counts, persisted across
 * restarts) — so someone who's been BLIND twice drifts toward DEAF/MUTED instead
 * of looping. Ties stay random (trial order is random), so rolls don't become a
 * predictable rotation either.
 *
 * <p>No-repeat rule (best effort): a re-roll should CHANGE your role. Scored as a
 * heavy penalty in the trial search, then guaranteed by a repair pass — first by
 * swapping with another player (only if the swap creates no new repeat), then,
 * when fewer players than roles are online, by trading with an undealt role from
 * the pool. The only unfixable case is forced by the fairness rule itself: 3+
 * players who ALL share one role (someone must take it back to keep full coverage).
 *
 * <p>NEVER BLOCKS (hard requirement): there is no retry-until-success anywhere —
 * a fixed number of trials, then the best deal found is applied as-is. History and
 * no-repeat are preferences expressed as scores; when they can't be satisfied
 * (odd player counts, someone absent last round, everyone sharing a role), the
 * roulette still completes with the least-bad deal.
 */
public final class RoleRoller {
    private RoleRoller() {}

    private static final Random RNG = new Random();

    /** Candidate deals tried per roll; the lowest-scoring one wins. Cheap (a few
     *  list shuffles each), so generous. */
    private static final int DEAL_TRIALS = 64;

    /** Score penalty for handing a player the role they already have — dwarfs any
     *  realistic history count, so a repeat only survives when every trial has one. */
    private static final int REPEAT_PENALTY = 1_000;

    /**
     * Assign a random disability to everyone in {@code players}.
     *
     * @return the number of players assigned.
     */
    public static int rollAll(List<ServerPlayerEntity> players, RoleManager roles) {
        if (players.isEmpty()) return 0;

        RoleHistory history = roles.history();
        List<ServerPlayerEntity> shuffled = new ArrayList<>(players);
        Collections.shuffle(shuffled, RNG);
        int poolSize = Role.ASSIGNABLE.length;

        // Best-of-N search: every trial is a valid coverage deal; score = how often
        // each player has already had the role they'd get (+ heavy repeat penalty).
        List<Role> assigned = null;
        List<Role> pool = null; // pool of the winning trial — its undealt tail feeds the repair pass
        int bestScore = Integer.MAX_VALUE;
        for (int t = 0; t < DEAL_TRIALS; t++) {
            List<Role> candPool = new ArrayList<>(Arrays.asList(Role.ASSIGNABLE));
            Collections.shuffle(candPool, RNG);
            List<Role> cand = new ArrayList<>(shuffled.size());
            for (int i = 0; i < shuffled.size(); i++) {
                cand.add(candPool.get(i % poolSize));
            }
            Collections.shuffle(cand, RNG); // decouple "who gets what" from pool order

            int score = 0;
            for (int i = 0; i < shuffled.size(); i++) {
                score += history.count(shuffled.get(i).getUuid(), cand.get(i));
                if (cand.get(i) == roles.get(shuffled.get(i))) score += REPEAT_PENALTY;
            }
            if (score < bestScore) {
                bestScore = score;
                assigned = cand;
                pool = candPool;
            }
        }

        // Repair pass: nobody should keep their current role if avoidable.
        for (int i = 0; i < shuffled.size(); i++) {
            Role current = roles.get(shuffled.get(i));
            if (assigned.get(i) != current) continue;

            boolean fixed = false;
            // Swap with another player, but only if that introduces no new repeat:
            // i must not receive j's current role, and j (who receives i's role,
            // i.e. `current`) must not currently hold it either.
            for (int j = 0; j < shuffled.size() && !fixed; j++) {
                if (j == i) continue;
                if (assigned.get(j) != current && roles.get(shuffled.get(j)) != current) {
                    Collections.swap(assigned, i, j);
                    fixed = true;
                }
            }
            // With fewer players than roles, the tail of the pool is undealt —
            // trade with it (write `current` back so a later repair sees the truth).
            for (int k = shuffled.size(); k < poolSize && !fixed; k++) {
                if (pool.get(k) != current) {
                    assigned.set(i, pool.get(k));
                    pool.set(k, current);
                    fixed = true;
                }
            }
        }

        // Coverage enforcement (hard requirement, belt + braces): with 3+ players EVERY
        // disability MUST be present. Structurally already true (cyclic deal covers all
        // roles; the 3+ repair path only swaps, preserving the multiset), but this pass
        // makes the invariant survive any future edit: any missing role forcibly replaces
        // a duplicated one — preferring a player for whom it isn't a repeat.
        if (shuffled.size() >= poolSize) {
            for (Role role : Role.ASSIGNABLE) {
                if (assigned.contains(role)) continue;
                int fallback = -1, pick = -1;
                for (int i = 0; i < assigned.size() && pick < 0; i++) {
                    if (Collections.frequency(assigned, assigned.get(i)) > 1) {
                        if (roles.get(shuffled.get(i)) != role) pick = i;
                        else if (fallback < 0) fallback = i;
                    }
                }
                // A duplicate always exists here (pigeonhole: players >= roles, one missing).
                assigned.set(pick >= 0 ? pick : fallback, role);
            }
        }

        for (int i = 0; i < shuffled.size(); i++) {
            roles.setAnimated(shuffled.get(i), assigned.get(i));
        }
        return shuffled.size();
    }
}
