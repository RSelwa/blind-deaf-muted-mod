package com.blinddeafmuted.client;

/**
 * Client-side mirror of the server's skin-visibility flag (see
 * {@code SkinVisibilityManager}). When {@code false}, the role accessory feature
 * renderers ({@link BlindCaneFeatureRenderer}, {@link RoleHeadAccessoryFeatureRenderer})
 * skip drawing, so no custom role "skins" appear.
 *
 * <p>The server pushes the value via {@code SkinVisibilityPayload}; the render thread
 * reads it, hence {@code volatile}. Defaults to {@code true} so accessories show until
 * told otherwise (matching the server default).
 */
public final class SkinVisibilityState {
    private SkinVisibilityState() {}

    private static volatile boolean enabled = true;

    public static boolean isEnabled() {
        return enabled;
    }

    public static void set(boolean value) {
        enabled = value;
    }
}
