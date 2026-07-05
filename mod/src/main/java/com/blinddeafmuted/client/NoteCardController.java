package com.blinddeafmuted.client;

import com.blinddeafmuted.common.CardBrandishPayload;
import com.blinddeafmuted.common.ModItems;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;

/**
 * Drives the note card on the client:
 * <ul>
 *   <li><b>Write</b> — the write key (default {@code G}, rebindable in Controls) opens the
 *       {@link CardEditScreen} while a card is in hand.</li>
 *   <li><b>Brandish</b> — right-clicking with a card in hand toggles brandishing it outward
 *       (Sea-of-Thieves style). We flip {@link CardBrandishState} instantly for a snappy local
 *       reaction and tell the server ({@link CardBrandishPayload}) so it echoes to everyone.</li>
 * </ul>
 *
 * <p>The toggle auto-resets when the card leaves the hand, so re-selecting the card always
 * starts in the private-read state (you see it, others don't).
 */
public final class NoteCardController {
    private NoteCardController() {}

    private static final KeyBinding WRITE_KEY = new KeyBinding(
            "key.blind-deaf-muted.write_card",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            "key.categories.blind-deaf-muted");

    public static void register() {
        KeyBindingHelper.registerKeyBinding(WRITE_KEY);

        // Right-click with a card in hand = toggle brandish. Return SUCCESS to consume the
        // click + swing; the server does nothing on use, so this is purely the show gesture.
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!world.isClient) return ActionResult.PASS;
            if (!player.getStackInHand(hand).isOf(ModItems.NOTE_CARD)) return ActionResult.PASS;
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

            // Open the editor on the write key, only while holding a card and with no screen up.
            while (WRITE_KEY.wasPressed()) {
                if (client.currentScreen != null) break;
                Hand hand = holdingHand(client.player);
                if (hand != null) {
                    client.setScreen(new CardEditScreen(hand));
                }
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
