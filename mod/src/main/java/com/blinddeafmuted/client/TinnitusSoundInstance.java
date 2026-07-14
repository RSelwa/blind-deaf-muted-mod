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
        this.repeat = false; // Do not loop indefinitely, we stop after 3 seconds
        this.repeatDelay = 0;
        this.relative = true;                       // coords relative to the listener…
        this.attenuationType = AttenuationType.NONE; // …and no distance falloff → in-head
        this.volume = 1.0F;
        this.pitch = 1.0F;
    }

    private int getFadeTicks() {
        return Math.max(1, (int) (ClientConfigState.get().deafReliefTinnitusFadeSeconds() * 20.0f));
    }

    private int getMaxTicks() {
        return (int) (ClientConfigState.get().deafReliefTinnitusDurationSeconds() * 20.0f);
    }

    private int ticksActive = 0;
    private boolean stopping = false;
    private float currentFade = 0.0f;

    @Override
    public void tick() {
        if (stopping) {
            currentFade -= 1.0f / getFadeTicks();
            if (currentFade <= 0.0f) {
                currentFade = 0.0f;
                setDone();
            }
            return;
        }

        if (player.isRemoved()
                || !(RoleState.is(Role.DEAF) && ReliefState.localActive())) {
            stopping = true;
            return;
        }

        ticksActive++;
        if (ticksActive >= getMaxTicks() - getFadeTicks()) {
            stopping = true;
            return;
        }

        if (currentFade < 1.0f) {
            currentFade += 1.0f / getFadeTicks();
            if (currentFade > 1.0f) {
                currentFade = 1.0f;
            }
        }
    }

    /**
     * Live volume: read the {@code deafReliefTinnitusVolume} config knob each time the sound
     * engine samples it, and apply our fade-in/fade-out envelope.
     */
    @Override
    public float getVolume() {
        return ClientConfigState.get().deafReliefTinnitusVolume() * currentFade;
    }
}
