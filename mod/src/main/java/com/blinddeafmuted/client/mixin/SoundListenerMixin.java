package com.blinddeafmuted.client.mixin;

import com.blinddeafmuted.client.DeafListenerGain;

import net.minecraft.client.sound.SoundListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * DEAF world-loudness boost: multiply the OpenAL <b>listener</b> gain by {@link DeafListenerGain}.
 *
 * <p>{@code SoundListener.setVolume(float)} calls {@code alListenerf(AL_GAIN, volume)} — the one
 * gain in the pipeline OpenAL does NOT cap at 1.0, so it's how a deaf player can turn the muffled
 * world back up (even past normal). We scale the incoming volume so the deaf boost rides on top of
 * whatever master volume Minecraft is setting; {@code boost()} is {@code 1.0} for everyone else, so
 * this is a no-op unless the local player is deaf. {@link DeafListenerGain} re-drives this on the
 * correct thread whenever the boost changes.
 */
@Mixin(SoundListener.class)
public class SoundListenerMixin {

    @ModifyVariable(method = "setVolume", at = @At("HEAD"), argsOnly = true)
    private float blinddeafmuted$boostForDeaf(float volume) {
        return volume * DeafListenerGain.boost();
    }
}
