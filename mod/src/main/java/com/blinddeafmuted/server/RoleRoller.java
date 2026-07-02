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
 * handed out at least once before any repeats. Shuffle the pool (randomises which
 * disabilities fill the distinct slots and which duplicate), shuffle the players
 * (randomises who gets what), then deal the pool out cyclically — cycling guarantees
 * full coverage before repeats.
 *
 * <p>No-repeat rule (best effort): a re-roll should CHANGE your role. After dealing,
 * any player who landed on their current role gets repaired — first by swapping with
 * another player (only if the swap creates no new repeat), then, when fewer players
 * than roles are online, by trading with an undealt role from the pool. The only
 * unfixable case is forced by the fairness rule itself: 3+ players who ALL share one
 * role (someone must take it back to keep full coverage).
 */
public final class RoleRoller {
    private RoleRoller() {}

    private static final Random RNG = new Random();

    /**
     * Assign a random disability to everyone in {@code players}.
     *
     * @return the number of players assigned.
     */
    public static int rollAll(List<ServerPlayerEntity> players, RoleManager roles) {
        if (players.isEmpty()) return 0;

        List<Role> pool = new ArrayList<>(Arrays.asList(Role.ASSIGNABLE));
        List<ServerPlayerEntity> shuffled = new ArrayList<>(players);
        Collections.shuffle(pool, RNG);
        Collections.shuffle(shuffled, RNG);

        int poolSize = Role.ASSIGNABLE.length;
        List<Role> assigned = new ArrayList<>(shuffled.size());
        for (int i = 0; i < shuffled.size(); i++) {
            assigned.add(pool.get(i % poolSize));
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

        for (int i = 0; i < shuffled.size(); i++) {
            roles.setAnimated(shuffled.get(i), assigned.get(i));
        }
        return shuffled.size();
    }
}
