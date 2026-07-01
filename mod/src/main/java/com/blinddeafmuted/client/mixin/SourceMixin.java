package com.blinddeafmuted.client.mixin;

import com.blinddeafmuted.client.DeafAudioFilter;
import com.blinddeafmuted.client.RoleState;
import com.blinddeafmuted.common.Role;
import net.minecraft.client.sound.Source;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * DEAF muffle: attach an OpenAL low-pass filter to this sound {@link Source} whenever the
 * local player is deaf (see {@link DeafAudioFilter}). Applied both when a sound starts
 * ({@code play}) and each {@code tick} — so a long streaming sound (music / cave ambience)
 * that was already playing when the player goes deaf gets muffled live too. Short one-shot
 * sounds are covered by {@code play}. These methods run on Minecraft's audio thread, so the
 * OpenAL calls are on the right context.
 */
@Mixin(Source.class)
public class SourceMixin {

    @Shadow @Final private int pointer;

    @Inject(method = "play", at = @At("HEAD"))
    private void blinddeafmuted$muffleOnPlay(CallbackInfo ci) {
        DeafAudioFilter.apply(pointer, RoleState.is(Role.DEAF));
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void blinddeafmuted$muffleOnTick(CallbackInfo ci) {
        DeafAudioFilter.apply(pointer, RoleState.is(Role.DEAF));
    }
}
