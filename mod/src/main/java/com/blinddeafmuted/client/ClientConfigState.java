package com.blinddeafmuted.client;

import com.blinddeafmuted.common.ModConfig;

/**
 * Client-side mirror of the server's live {@link ModConfig}. The server pushes a
 * {@code ConfigPayload} on join and after every change; the receiver in
 * {@code BlindDeafMutedClient} drops the new snapshot here.
 *
 * <p>Read by the pure-client effect mixins (fog distance in {@code BackgroundRendererMixin},
 * ambient loudness in {@code SoundSystemMixin}) and by {@code ConfigScreen} to seed its sliders.
 * {@code volatile} because it's written from a networking callback and read from the render /
 * client-tick threads.
 */
public final class ClientConfigState {
    private ClientConfigState() {}

    private static volatile ModConfig current = ModConfig.DEFAULT;

    public static ModConfig get() {
        return current;
    }

    public static void set(ModConfig config) {
        current = config;
    }
}
