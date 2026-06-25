package com.monkeys.client.mixin;

import com.monkeys.client.RoleState;
import com.monkeys.common.Role;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * DEAF effect: cancel every sound at the source while the local player is deaf.
 *
 * <p>This is the clean, settings-free way to be deaf. We do NOT touch the player's
 * volume sliders (that would be a nightmare to snapshot/restore every time an admin
 * moves someone between roles). Instead we intercept playback itself: while deaf,
 * {@link SoundSystem#play} is short-circuited, so NOTHING new plays — music, mobs,
 * UI, footsteps, everything. Delayed sounds route through this same method, so
 * they're covered too. Currently-playing sounds are stopped by {@code DeafHandler}
 * on the transition into deafness.
 *
 * <p>The instant the player stops being deaf, this stops cancelling and sound
 * resumes naturally — with the player's original volume settings completely intact.
 */
@Mixin(SoundSystem.class)
public class SoundSystemMixin {

    @Inject(
            method = "play(Lnet/minecraft/client/sound/SoundInstance;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void monkeys$muteWhenDeaf(SoundInstance sound, CallbackInfo ci) {
        if (RoleState.is(Role.DEAF)) {
            ci.cancel();
        }
    }
}
