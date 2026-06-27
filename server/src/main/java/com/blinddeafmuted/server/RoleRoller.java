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
        for (int i = 0; i < shuffled.size(); i++) {
            roles.setAnimated(shuffled.get(i), pool.get(i % poolSize));
        }
        return shuffled.size();
    }
}
