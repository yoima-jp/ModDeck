package com.yoima.moddeck.api.option;

import com.yoima.moddeck.api.OptionPresentation;
import java.util.Objects;

/** A typed, mutable configuration value and its UI-independent metadata. */
public abstract class ConfigOption<T> {
    private final String id;
    private final String displayName;
    private final String description;
    private final T defaultValue;
    private final OptionPresentation presentation;
    private T value;

    protected ConfigOption(String id, String displayName, String description, T defaultValue,
                           OptionPresentation presentation) {
        this.id = requireIdentifier(id, "option id");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.description = Objects.requireNonNullElse(description, "");
        this.defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
        this.presentation = Objects.requireNonNull(presentation, "presentation");
        this.value = defaultValue;
    }

    public final String id() { return id; }
    public final String displayName() { return displayName; }
    public final String description() { return description; }
    public final T defaultValue() { return defaultValue; }
    public final T value() { return value; }
    public final OptionPresentation presentation() { return presentation; }

    public final void setValue(T value) {
        this.value = validate(Objects.requireNonNull(value, "value"));
    }

    public final void reset() {
        value = defaultValue;
    }

    /** Converts a storage-neutral value and rejects invalid input. */
    public abstract T decode(Object value);

    protected T validate(T value) { return value; }

    private static String requireIdentifier(String value, String label) {
        Objects.requireNonNull(value, label);
        if (!value.matches("[a-z0-9_.-]+")) {
            throw new IllegalArgumentException(label + " must match [a-z0-9_.-]+: " + value);
        }
        return value;
    }
}
