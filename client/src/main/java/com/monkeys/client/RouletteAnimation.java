package com.monkeys.client;

import com.monkeys.common.Role;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * The "roulette" reveal: a slot-machine overlay that spins through the disability
 * roles, decelerates, and lands on the role the server rolled for this player —
 * after which the real effect is applied.
 *
 * <p>Triggered by {@link com.monkeys.common.RollPayload} (the random roll / re-roll
 * bottle), NOT by ordinary {@code RolePayload} syncs. The reel is stepped from the
 * client tick ({@link #register}, 20/s) so the "tick" sound lines up exactly with the
 * visible role change; drawing happens from {@code InGameHudMixin} at the TAIL of HUD
 * rendering, on top of everything else.
 *
 * <p>Timeline: {@value #SPIN_TICKS} ticks spinning (decelerating, ease-out) →
 * {@value #HOLD_TICKS} ticks holding the result ("You're now BLIND!") → apply the
 * effect via {@link RoleState#set}. At 20 ticks/sec that's ~2.25s spin + ~1s hold.
 */
public final class RouletteAnimation {
    private RouletteAnimation() {}

    /** The reel: only the assignable disabilities cycle past (NONE never spins by). */
    private static final Role[] REEL = Role.ASSIGNABLE;

    private static final int SPIN_TICKS = 45;
    private static final int HOLD_TICKS = 20;
    private static final int TOTAL_TICKS = SPIN_TICKS + HOLD_TICKS;

    /** How many reel steps a "full speed" spin would advance — sets the spin count
     *  before the ease-out brings it to rest. 5 full loops then onto the target. */
    private static final int BASE_LOOPS = 5;

    private static boolean active = false;
    private static int ticksRemaining = 0;
    private static Role target = Role.NONE;

    /** The role currently shown in the reel (updated each tick, read each frame). */
    private static Role shown = Role.NONE;
    /** Last reel index we ticked on, so we only click + advance on an actual change. */
    private static int lastIndex = -1;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick(client));
    }

    /** Begin the reveal for {@code finalRole}. Restarts cleanly if one is in flight. */
    public static void start(Role finalRole) {
        target = finalRole;
        ticksRemaining = TOTAL_TICKS;
        shown = REEL.length > 0 ? REEL[0] : finalRole;
        lastIndex = -1;
        active = true;
        // Freeze the leaderboard so it keeps showing the OLD roles until the reveal —
        // otherwise it spoils the result mid-spin.
        RosterState.freeze();
    }

    /** Whether a roulette reveal is currently playing (used to gate other HUD bits). */
    public static boolean isActive() {
        return active;
    }

    private static void tick(MinecraftClient client) {
        if (!active) return;
        ticksRemaining--;

        int elapsed = TOTAL_TICKS - ticksRemaining;
        if (elapsed <= SPIN_TICKS) {
            double progress = (double) elapsed / SPIN_TICKS;        // 0 → 1
            double eased = 1.0 - Math.pow(1.0 - progress, 3.0);     // ease-out cubic
            int n = REEL.length;
            // Total steps chosen so the final step lands exactly on the target index.
            int totalSteps = BASE_LOOPS * n + indexOf(target);
            int step = (int) Math.floor(eased * totalSteps);
            int index = step % n;
            if (index != lastIndex) {
                lastIndex = index;
                shown = REEL[index];
                // Pitch rises as the reel slows — the classic "winding to a stop" feel.
                client.getSoundManager().play(PositionedSoundInstance.master(
                        SoundEvents.UI_BUTTON_CLICK, 1.0f + (float) progress));
            }
        } else {
            shown = target;
        }

        if (ticksRemaining <= 0) {
            // Fanfare first (so a player rolling DEAF still hears their reveal), then
            // apply the real role + effect exactly at the end of the hold.
            client.getSoundManager().play(PositionedSoundInstance.master(
                    SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f));
            RoleState.set(target);
            RosterState.unfreeze(); // reveal the new roles on the leaderboard now
            active = false;
        }
    }

    /** Draw the overlay. Called from InGameHudMixin at the TAIL of HUD render. */
    public static void render(DrawContext context) {
        if (!active) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        TextRenderer font = mc.textRenderer;

        boolean spinning = (TOTAL_TICKS - ticksRemaining) <= SPIN_TICKS;

        int cx = context.getScaledWindowWidth() / 2;
        int cy = context.getScaledWindowHeight() / 2;
        int w = context.getScaledWindowWidth();

        // A dim banner band behind the text so it reads over any scene.
        context.fill(0, cy - 32, w, cy + 28, 0xB0000000);

        Text caption = Text.literal(spinning ? "Assigning your role…" : "You're now")
                .formatted(Formatting.WHITE);
        context.drawCenteredTextWithShadow(font, caption, cx, cy - 24, 0xFFFFFFFF);

        // The big role word, scaled up, in its role colour.
        Text roleText = Text.literal(shown.name()).formatted(shown.color(), Formatting.BOLD);
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(cx, cy - 6, 0);
        matrices.scale(3.0f, 3.0f, 1.0f);
        int halfWidth = font.getWidth(roleText) / 2;
        context.drawTextWithShadow(font, roleText, -halfWidth, 0, 0xFFFFFFFF);
        matrices.pop();
    }

    private static int indexOf(Role r) {
        for (int i = 0; i < REEL.length; i++) {
            if (REEL[i] == r) return i;
        }
        return 0; // target not on the reel (e.g. NONE) — harmless, hold shows it anyway
    }
}
