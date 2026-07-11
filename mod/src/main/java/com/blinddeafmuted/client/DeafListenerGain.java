package com.blinddeafmuted.client;

import com.blinddeafmuted.common.Role;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.MathHelper;

/**
 * DEAF world-loudness boost, applied on the OpenAL <b>listener</b> gain.
 *
 * <p>Why the listener and not the source: the deaf muffle strips the highs, so the world reads
 * quiet, and players want to crank it back up — sometimes above normal. But a per-source
 * {@code AL_GAIN} can't exceed 1.0 (OpenAL clamps the effective gain to {@code AL_MAX_GAIN},
 * which itself is capped at 1.0), so a {@code >1} boost on {@code getAdjustedVolume} is silently
 * lost. The <em>listener</em> gain ({@code alListenerf(AL_GAIN, …)}) has no such ceiling, so we
 * put the whole {@code deafEnvVolume} knob there instead. Since the muffle filter is attached to
 * EVERY source while deaf (see {@code SourceMixin}), boosting the global listener just compensates
 * that muffle uniformly — nothing is special-cased.
 *
 * <p>{@link SoundListenerMixin} multiplies every {@code SoundListener.setVolume} call by
 * {@link #boost()}. Minecraft only pushes the listener volume when the master-volume option
 * changes, so we also watch {@link #boost()} each client tick and, when it moves (deaf toggled,
 * Relief kicking in/out, or the slider dragged), re-drive {@code updateSoundVolume(MASTER, …)} to
 * force the mixin to re-apply — no restart, live.
 */
public final class DeafListenerGain {
    private DeafListenerGain() {}

    /** Last boost we pushed, so we only re-drive the listener when it actually changes. */
    private static float lastBoost = 1.0f;

    /**
     * Listener {@code AL_GAIN} multiplier: {@code 1.0} normally, the {@code deafEnvVolume} knob
     * while the local player is DEAF ({@code <1} quieter, {@code >1} louder). A Potion of Relief
     * lerps it back toward {@code 1.0} (normal hearing) as the disability fades.
     */
    public static float boost() {
        if (!RoleState.is(Role.DEAF)) return 1.0f;
        float rem = ReliefState.disabilityRemaining(); // 1 = full deafness, 0 = fully relieved
        float target = ClientConfigState.get().deafEnvVolume();
        return MathHelper.lerp(rem, 1.0f, target); // rem=1 → target boost, rem=0 → normal
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.getSoundManager() == null || client.options == null) return;
            float b = boost();
            if (Math.abs(b - lastBoost) > 1.0e-4f) {
                lastBoost = b;
                // Re-push the raw master volume; SoundListenerMixin multiplies it by boost() again.
                float master = client.options.getSoundVolume(SoundCategory.MASTER);
                client.getSoundManager().updateSoundVolume(SoundCategory.MASTER, master);
            }
        });
    }
}
