package com.blinddeafmuted.server;

import com.blinddeafmuted.common.ModConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Server-side source of truth for the live {@link ModConfig}.
 *
 * <p>Holds one authoritative snapshot, loaded from {@code config/blind-deaf-muted.json} at
 * startup (or {@link ModConfig#DEFAULT} if the file is missing/corrupt) and rewritten on every
 * change, so tuned values survive a restart — the whole point of moving them off
 * {@code static final} constants.
 *
 * <p>This class only stores + persists. Broadcasting the new config to clients and applying
 * the side-effects (re-arming the event timer) is the caller's job in
 * {@link BlindDeafMutedServer}, keeping this decoupled from networking and the server tick.
 *
 * <p>{@code current} is {@code volatile}: {@code VoiceFx} reads it off SVC's own audio threads
 * while the server thread writes it from a slider update, so we need the visibility guarantee.
 */
public final class ConfigManager {

    private static final Path FILE =
            FabricLoader.getInstance().getConfigDir().resolve("blind-deaf-muted.json");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private volatile ModConfig current;

    public ConfigManager() {
        this.current = load();
    }

    /** The live config. Never null. */
    public ModConfig get() {
        return current;
    }

    /** Replace the config and persist it to disk. Broadcast + side-effects are the caller's job. */
    public void set(ModConfig config) {
        this.current = config;
        save(config);
    }

    // ---- JSON persistence --------------------------------------------------

    private ModConfig load() {
        if (!Files.exists(FILE)) {
            save(ModConfig.DEFAULT); // materialise a template the host can hand-edit too
            return ModConfig.DEFAULT;
        }
        try {
            JsonObject o = GSON.fromJson(Files.readString(FILE), JsonObject.class);
            ModConfig d = ModConfig.DEFAULT;
            // Read each field, falling back to the default if absent — so a config written by
            // an older version (fewer keys) still loads, gaining the new fields at their default.
            return new ModConfig(
                    f(o, "deafLowpassHz", d.deafLowpassHz()),
                    f(o, "deafVolume", d.deafVolume()),
                    f(o, "deafMegaphoneLowpassHz", d.deafMegaphoneLowpassHz()),
                    f(o, "deafMegaphoneVolume", d.deafMegaphoneVolume()),
                    f(o, "mutedLowpassHz", d.mutedLowpassHz()),
                    f(o, "mutedVolume", d.mutedVolume()),
                    f(o, "mutedMegaphoneLowpassHz", d.mutedMegaphoneLowpassHz()),
                    f(o, "mutedMegaphoneVolume", d.mutedMegaphoneVolume()),
                    f(o, "blindFogHardEnd", d.blindFogHardEnd()),
                    f(o, "blindFogMediumEnd", d.blindFogMediumEnd()),
                    f(o, "deafEnvVolume", d.deafEnvVolume()),
                    f(o, "eventMinMinutes", d.eventMinMinutes()),
                    f(o, "eventMaxMinutes", d.eventMaxMinutes()),
                    f(o, "randomizerChestChance", d.randomizerChestChance()));
        } catch (IOException | RuntimeException e) {
            BlindDeafMutedServer.LOGGER.warn("Failed to read {} — using defaults ({})",
                    FILE, e.toString());
            return ModConfig.DEFAULT;
        }
    }

    private void save(ModConfig c) {
        JsonObject o = new JsonObject();
        o.addProperty("deafLowpassHz", c.deafLowpassHz());
        o.addProperty("deafVolume", c.deafVolume());
        o.addProperty("deafMegaphoneLowpassHz", c.deafMegaphoneLowpassHz());
        o.addProperty("deafMegaphoneVolume", c.deafMegaphoneVolume());
        o.addProperty("mutedLowpassHz", c.mutedLowpassHz());
        o.addProperty("mutedVolume", c.mutedVolume());
        o.addProperty("mutedMegaphoneLowpassHz", c.mutedMegaphoneLowpassHz());
        o.addProperty("mutedMegaphoneVolume", c.mutedMegaphoneVolume());
        o.addProperty("blindFogHardEnd", c.blindFogHardEnd());
        o.addProperty("blindFogMediumEnd", c.blindFogMediumEnd());
        o.addProperty("deafEnvVolume", c.deafEnvVolume());
        o.addProperty("eventMinMinutes", c.eventMinMinutes());
        o.addProperty("eventMaxMinutes", c.eventMaxMinutes());
        o.addProperty("randomizerChestChance", c.randomizerChestChance());
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(o));
        } catch (IOException e) {
            BlindDeafMutedServer.LOGGER.warn("Failed to write {} ({})", FILE, e.toString());
        }
    }

    private static float f(JsonObject o, String key, float fallback) {
        return o.has(key) ? o.get(key).getAsFloat() : fallback;
    }
}
