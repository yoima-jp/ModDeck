package com.yoima.moddeck.api;

import java.util.Arrays;
import java.util.Objects;
import net.minecraft.network.chat.Component;

/**
 * Language-neutral UI text. Translation keys are resolved only when a screen is rendered, so
 * resources supplied by the registering mod and runtime language changes are both respected.
 */
public final class ConfigText {
    private static final ConfigText EMPTY = literal("");
    private final String value;
    private final boolean translationKey;
    private final Object[] arguments;

    private ConfigText(String value, boolean translationKey, Object[] arguments) {
        this.value = Objects.requireNonNull(value, "value");
        this.translationKey = translationKey;
        this.arguments = arguments.clone();
    }

    public static ConfigText empty() { return EMPTY; }
    public static ConfigText literal(String text) { return new ConfigText(text, false, new Object[0]); }
    public static ConfigText translatable(String key, Object... arguments) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("Translation key must not be blank");
        return new ConfigText(key, true, Objects.requireNonNull(arguments, "arguments"));
    }

    public String value() { return value; }
    public boolean isTranslationKey() { return translationKey; }
    public Object[] arguments() { return arguments.clone(); }

    /** Creates a fresh component to avoid retaining text resolved under a previous language. */
    public Component component() {
        return translationKey ? Component.translatable(value, arguments) : Component.literal(value);
    }

    public boolean isEmpty() { return value.isEmpty(); }

    @Override public boolean equals(Object other) {
        return other instanceof ConfigText text && value.equals(text.value)
                && translationKey == text.translationKey && Arrays.equals(arguments, text.arguments);
    }

    @Override public int hashCode() { return 31 * Objects.hash(value, translationKey) + Arrays.hashCode(arguments); }
    @Override public String toString() { return (translationKey ? "key:" : "literal:") + value; }
}
