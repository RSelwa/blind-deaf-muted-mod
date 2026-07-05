package com.blinddeafmuted.common;

import com.mojang.serialization.Codec;
import net.minecraft.component.ComponentType;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

import java.util.List;

/**
 * Custom data components shared by both jars. Registered once from the {@code main}
 * entrypoint ({@code BlindDeafMutedServer}, which runs on both sides) so the component
 * type exists with the same id on the server AND every client.
 *
 * <p>{@link #CARD_TEXT} holds the lines a player wrote on a {@link ModItems#NOTE_CARD}.
 * It carries BOTH a persistent {@link Codec} (so the text survives save/load and item
 * moves) and a {@link PacketCodec} (so a held card's text is synced to the other clients
 * tracking that player — that's how everyone reads a brandished card without any extra
 * packet: the note-card feature renderer just reads this component off the tracked stack).
 */
public final class ModComponents {
    private ModComponents() {}

    /** The lines written on a note card (≤ {@link #MAX_LINES}). Assigned in {@link #register()}. */
    public static ComponentType<List<String>> CARD_TEXT;

    /** Sign-like caps: at most 6 lines, each at most this many characters. */
    public static final int MAX_LINES = 6;
    public static final int MAX_LINE_LENGTH = 22;

    public static void register() {
        // Idempotent: the unified jar can reach register() from more than one entrypoint
        // on a physical client. Registering twice would throw, so bail if we've run.
        if (CARD_TEXT != null) return;
        CARD_TEXT = Registry.register(
                Registries.DATA_COMPONENT_TYPE,
                ModConstants.id("card_text"),
                ComponentType.<List<String>>builder()
                        .codec(Codec.STRING.listOf())
                        .packetCodec(PacketCodecs.STRING.collect(PacketCodecs.toList()))
                        .build());
    }
}
