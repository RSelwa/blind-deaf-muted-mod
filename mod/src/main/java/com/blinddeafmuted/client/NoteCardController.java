package com.blinddeafmuted.client;

import com.blinddeafmuted.common.CardBrandishPayload;
import com.blinddeafmuted.common.ModItems;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

/**
 * Drives the note card on the client — both gestures live on right-click now
 * (was: a separate rebindable write key, default {@code G}):
 * <ul>
 *   <li><b>Brandish</b> — right-click with a card in hand toggles brandishing it outward
 *       (Sea-of-Thieves style). We flip {@link CardBrandishState} instantly for a snappy local
 *       reaction and tell the server ({@link CardBrandishPayload}) so it echoes to everyone.</li>
 *   <li><b>Write</b> — sneak (shift) + right-click opens the {@link CardEditScreen} instead
 *       of toggling.</li>
 * </ul>
 *
 * <p>The toggle auto-resets when the card leaves the hand, so re-selecting the card always
 * starts in the private-read state (you see it, others don't).
 */
public final class NoteCardController {
    private NoteCardController() {}

    public static void register() {
        // Right-click with a card in hand: sneaking → open the write screen; otherwise toggle
        // brandish. Return SUCCESS to consume the click + swing; the server does nothing on
        // use (the card is a plain item), so both are purely client gestures.
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!world.isClient) return ActionResult.PASS;
            if (!player.getStackInHand(hand).isOf(ModItems.NOTE_CARD)) return ActionResult.PASS;

            if (player.isSneaking()) {
                // The callback runs on the render thread during input handling, so opening
                // the screen directly is safe (same thread setScreen is always called on).
                MinecraftClient.getInstance().setScreen(new CardEditScreen(hand));
                return ActionResult.SUCCESS;
            }

            boolean now = CardBrandishState.toggleLocal();
            if (ClientPlayNetworking.canSend(CardBrandishPayload.ID)) {
                ClientPlayNetworking.send(new CardBrandishPayload(now));
            }
            return ActionResult.SUCCESS;
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) {
                CardBrandishState.clearLocal();
                return;
            }

            // Stopped holding a card → drop the brandish toggle so it never lingers on the next card.
            if (holdingHand(client.player) == null && CardBrandishState.localActive()) {
                CardBrandishState.clearLocal();
                if (ClientPlayNetworking.canSend(CardBrandishPayload.ID)) {
                    ClientPlayNetworking.send(new CardBrandishPayload(false));
                }
            }
        });
    }

    /** Which hand holds the note card, or {@code null} if neither does. Main hand wins. */
    public static Hand holdingHand(PlayerEntity player) {
        if (player.getMainHandStack().isOf(ModItems.NOTE_CARD)) return Hand.MAIN_HAND;
        if (player.getOffHandStack().isOf(ModItems.NOTE_CARD)) return Hand.OFF_HAND;
        return null;
    }
}
