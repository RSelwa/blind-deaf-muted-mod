package com.blinddeafmuted.common;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;

import java.util.function.Function;

/**
 * Item registrations shared by both jars. {@link #register()} is called once from
 * each entrypoint ({@code BlindDeafMutedServer} on a dedicated server, {@code BlindDeafMutedClient}
 * on a connecting client) so the item exists with the same id on both sides.
 */
public final class ModItems {
    private ModItems() {}

    /** The throwable re-roll bottle. Assigned in {@link #register()}. */
    public static Item RANDOMIZER;

    public static void register() {
        // Idempotent: in the unified jar this can be reached from more than one
        // entrypoint on a physical client. Registering twice would throw, so bail
        // if we've already run.
        if (RANDOMIZER != null) return;
        RANDOMIZER = register("randomizer",
                RandomizerItem::new,
                new Item.Settings().maxCount(16));
    }

    private static Item register(String path, Function<Item.Settings, Item> factory, Item.Settings settings) {
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, ModConstants.id(path));
        // 1.21.2+ requires the registry key to be set on the settings before construction.
        return Registry.register(Registries.ITEM, key, factory.apply(settings.registryKey(key)));
    }
}
