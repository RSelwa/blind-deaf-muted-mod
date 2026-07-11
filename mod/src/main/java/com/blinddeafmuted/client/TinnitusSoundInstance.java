package com.blinddeafmuted.client;

import com.blinddeafmuted.common.ModSounds;
import com.blinddeafmuted.common.Role;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.random.Random;

/**
 * The looping tinnitus (ear-ringing) heard ONLY by the local DEAF player while they're
 * under a Potion of Relief — the deaf mirror of the BLIND player's nausea wobble
 * (relief's downside). Non-positional and relative (attenuation NONE + relative), so it
 * sits "in your head" at a constant volume regardless of where you look or move.
 *
 * <p>Loops until the effect ends: {@link #tick()} marks the instance done the moment the
 * player stops being DEAF-with-relief (or is removed), so {@link DeafReliefTinnitus} can
 * restart a fresh one next time.
 */
public final class TinnitusSoundInstance extends MovingSoundInstance {

    private final ClientPlayerEntity player;

    TinnitusSoundInstance(ClientPlayerEntity player) {
        super(ModSounds.DEAF_RELIEF_TINNITUS, SoundCategory.MASTER, Random.create());
        this.player = player;
        this.repeat = true;
        this.repeatDelay = 0;
        this.relative = true;                       // coords relative to the listener…
        this.attenuationType = AttenuationType.NONE; // …and no distance falloff → in-head
        this.volume = 1.0F;
        this.pitch = 1.0F;
    }

    /**
     * Live volume: read the {@code deafReliefTinnitusVolume} config knob each time the sound
     * engine samples it, so dragging the slider in the config menu changes the ringing
     * loudness immediately without recreating the looping instance.
     */
    @Override
    public float getVolume() {
        return ClientConfigState.get().deafReliefTinnitusVolume();
    }

    @Override
    public void tick() {
        if (player.isRemoved()
                || !(RoleState.is(Role.DEAF) && ReliefState.localActive())) {
            setDone();
        }
    }
}
