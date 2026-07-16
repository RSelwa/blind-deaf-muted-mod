package com.blinddeafmuted.server;

import com.blinddeafmuted.common.Role;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player memory of how often each disability has been rolled, persisted to
 * {@code config/blind-deaf-muted-roles.json} so it survives a server restart
 * (a play session usually spans several restarts).
 *
 * <p>Why: pure random rolls cluster — one player can land BLIND round after round
 * ("I was often blind"). {@link RoleRoller} reads these counts and prefers deals
 * that give each player the roles they've had LEAST, evening the distribution out
 * over a session without breaking the coverage rule.
 *
 * <p>Every assignment path records here via {@link RoleManager} (roll, bottle,
 * auto-event, manual {@code /bdm set}) — a manual set still means you "spent" a
 * turn on that role, so the next roll steers you elsewhere. {@link Role#NONE} is
 * not recorded (it's a clear, not a turn).
 *
 * <p>Server-thread only (roll + set both happen there); no locking needed.
 */
public final class RoleHistory {

    private static final Logger LOGGER = LoggerFactory.getLogger("blind-deaf-muted");
    private static final Path FILE =
            FabricLoader.getInstance().getConfigDir().resolve("blind-deaf-muted-roles.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<UUID, EnumMap<Role, Integer>> counts = new HashMap<>();

    public RoleHistory() {
        load();
    }

    /** How many times this player has been given this role (0 for unknown players). */
    public int count(UUID uuid, Role role) {
        EnumMap<Role, Integer> perRole = counts.get(uuid);
        return perRole == null ? 0 : perRole.getOrDefault(role, 0);
    }

    /** Bump the counter for a real disability and persist. {@link Role#NONE} is ignored. */
    public void record(UUID uuid, Role role) {
        if (role == Role.NONE) return;
        counts.computeIfAbsent(uuid, u -> new EnumMap<>(Role.class)).merge(role, 1, Integer::sum);
        save();
    }

    // ---- JSON persistence ({"<uuid>": {"BLIND": 2, "DEAF": 1}, ...}) --------

    private void load() {
        if (!Files.exists(FILE)) return;
        try {
            JsonObject root = GSON.fromJson(Files.readString(FILE), JsonObject.class);
            if (root == null) return;
            for (Map.Entry<String, JsonElement> player : root.entrySet()) {
                UUID uuid = UUID.fromString(player.getKey());
                EnumMap<Role, Integer> perRole = new EnumMap<>(Role.class);
                for (Map.Entry<String, JsonElement> e : player.getValue().getAsJsonObject().entrySet()) {
                    perRole.put(Role.valueOf(e.getKey()), e.getValue().getAsInt());
                }
                counts.put(uuid, perRole);
            }
        } catch (IOException | RuntimeException e) {
            // Corrupt/old-format file: start fresh rather than crash — this is only
            // fairness memory, losing it costs one lopsided roll at worst.
            LOGGER.warn("Could not read {}, starting with an empty role history", FILE, e);
            counts.clear();
        }
    }

    private void save() {
        JsonObject root = new JsonObject();
        for (Map.Entry<UUID, EnumMap<Role, Integer>> player : counts.entrySet()) {
            JsonObject perRole = new JsonObject();
            for (Map.Entry<Role, Integer> e : player.getValue().entrySet()) {
                perRole.addProperty(e.getKey().name(), e.getValue());
            }
            root.add(player.getKey().toString(), perRole);
        }
        try {
            Files.writeString(FILE, GSON.toJson(root));
        } catch (IOException e) {
            LOGGER.warn("Could not write {}", FILE, e);
        }
    }
}
