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

    /** Held item that lets you talk <em>through</em> a DEAF player's near-silence:
     *  while a speaker holds it, the voice-chat plugin renders their voice loud and
     *  saturated for deaf listeners instead of near-inaudible. A plain item — its only
     *  effect is being read from the player's hand server-side. */
    public static Item MEGAPHONE;

    /** The blind player's cane: while a BLIND player holds it, their own client upgrades
     *  the full blackout to the reduced "see your feet" fog. Read locally on the client
     *  (no networking) — a plain item whose only effect is being held. */
    public static Item CANE;

    /** The MUTED player's note card: a square of paper you can write ≤6 lines on (like a
     *  sign) and brandish to show teammates (Sea-of-Thieves treasure-map style). The text
     *  lives in the {@link ModComponents#CARD_TEXT} component; the effect is client render +
     *  a write/brandish handshake. A plain item — all behaviour is in the client + the
     *  card payloads. */
    public static Item NOTE_CARD;

    /** Throwable "Potion of Relief" — a splash-style bottle (craft: water potion + diamond +
     *  lapis). On shatter, every player within range has their disability temporarily reduced
     *  (default 75%). Meant as a co-op boost for the Ender Dragon fight. Behaviour lives in
     *  {@link ReliefPotionEntity} (server shatter handler) + the effect scaling. */
    public static Item RELIEF_POTION;

    public static void register() {
        // Idempotent: in the unified jar this can be reached from more than one
        // entrypoint on a physical client. Registering twice would throw, so bail
        // if we've already run.
        if (RANDOMIZER != null) return;
        RANDOMIZER = register("randomizer",
                RandomizerItem::new,
                new Item.Settings().maxCount(16));
        MEGAPHONE = register("megaphone",
                Item::new,
                new Item.Settings().maxCount(1));
        CANE = register("cane",
                Item::new,
                new Item.Settings().maxCount(1));
        NOTE_CARD = register("note_card",
                Item::new,
                new Item.Settings().maxCount(1));
        RELIEF_POTION = register("relief_potion",
                ReliefPotionItem::new,
                new Item.Settings().maxCount(16));
    }

    private static Item register(String path, Function<Item.Settings, Item> factory, Item.Settings settings) {
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, ModConstants.id(path));
        // 1.21.2+ requires the registry key to be set on the settings before construction.
        return Registry.register(Registries.ITEM, key, factory.apply(settings.registryKey(key)));
    }
}
