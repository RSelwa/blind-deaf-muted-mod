package com.blinddeafmuted.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.blinddeafmuted.client.DeafMuffle;
import com.blinddeafmuted.client.DeafState;
import com.blinddeafmuted.client.ReliefState;
import com.blinddeafmuted.client.RoleState;
import com.blinddeafmuted.common.Role;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.util.math.MathHelper;

/**
 * DEAF effect on Minecraft's OWN audio (blocks, mobs, weather, music, footsteps — NOT
 * voice; that's Simple Voice Chat's path, shaped in {@code VoiceFx}). Two things here,
 * both keyed off the current {@link DeafMuffle} level:
 * <ul>
 *   <li><b>Slight loudness trim</b> — {@link #DEAF_ENV_VOLUME} knocks a little off overall
 *       (1.0 = none; each level also carries its own gain). The real muffle is the OpenAL
 *       low-pass in {@code SourceMixin}/{@code DeafAudioFilter}.</li>
 *   <li><b>Hearing range cap</b> — positional sounds fade out over the far half of
 *       {@link DeafMuffle#range()} and are silenced past it, so a deaf player hears only
 *       what's close (e.g. EXTREME = nothing past ~8 blocks). Non-positional sounds
 *       (UI / music / global ambience) are left alone — they have no meaningful distance.</li>
 * </ul>
 *
 * <p>We never touch the player's volume sliders, so full hearing returns the instant
 * deafness ends.
 */
@Mixin(SoundSystem.class)
public class SoundSystemMixin {

    /** Hearing radius (blocks) the deaf range-cap is eased toward under a full Potion of Relief. */
    private static final float RELIEF_RANGE = 48.0F;

    @ModifyReturnValue(
            method = "getAdjustedVolume(Lnet/minecraft/client/sound/SoundInstance;)F",
            at = @At("RETURN"))
    private float blinddeafmuted$deafenVolume(float original, SoundInstance sound) {
        if (!RoleState.is(Role.DEAF)) return original;

        // A Potion of Relief eases the deafness: rem=1 → full effect, rem→0 → back to normal.
        float rem = ReliefState.disabilityRemaining();

        // NOTE: overall deaf-world LOUDNESS (the deafEnvVolume knob, incl. amplification >1) is NOT
        // applied here — a per-source AL_GAIN can't exceed 1.0 (OpenAL clamps it to AL_MAX_GAIN,
        // which itself maxes at 1.0), so a boost here would be silently capped. It's applied on the
        // listener gain instead (see DeafListenerGain + SoundListenerMixin), which IS uncapped.
        // Here we only do the distance (hearing-range) cap.
        float v = original;

        // Distance cap: only for positional (attenuated) sounds — global UI/music have no
        // position, leave them. Fade from full at half-range to silent at full range.
        if (sound.getAttenuationType() == SoundInstance.AttenuationType.LINEAR) {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            if (player != null) {
                // Relief pushes the hearing radius back out toward normal.
                float range = MathHelper.lerp(rem, RELIEF_RANGE, DeafState.getMuffle().range());
                double dx = sound.getX() - player.getX();
                double dy = sound.getY() - player.getY();
                double dz = sound.getZ() - player.getZ();
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                float half = range * 0.5F;
                // 1 at/below half-range → 0 at/above full range; 0 past it.
                float fade = 1.0F - MathHelper.clamp((float) (dist - half) / (range - half), 0.0F, 1.0F);
                v *= fade;
            }
        }
        return v;
    }
}
