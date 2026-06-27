package com.blinddeafmuted.server;

/**
 * Optional "skin visibility" mode: stores whether player skins are currently
 * shown. Toggled live via {@code /bdm skin <on|off>}.
 *
 * <p>This class is just the server-side switch. The actual hiding/showing of
 * skins is applied on the client; this flag is the source of truth the rest of
 * the server reads.
 */
public class SkinVisibilityManager {
    /** Whether skin visibility is currently active. On by default. */
    private boolean enabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setSkinsEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
