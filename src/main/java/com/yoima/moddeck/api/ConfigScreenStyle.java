package com.yoima.moddeck.api;

import java.util.Objects;

/** Client-independent screen customization metadata interpreted by Mod Deck's renderer. */
public record ConfigScreenStyle(boolean transparentBackground, String backgroundTexture,
                                int accentColor, boolean confirmSave) {
    /** An accent of zero keeps the current Mod Deck theme accent. */
    public static final ConfigScreenStyle DEFAULT = new ConfigScreenStyle(false, "", 0, false);

    public ConfigScreenStyle {
        backgroundTexture = Objects.requireNonNullElse(backgroundTexture, "");
        if (!backgroundTexture.isEmpty() && !backgroundTexture.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) {
            throw new IllegalArgumentException("Invalid background texture identifier: " + backgroundTexture);
        }
    }
}
