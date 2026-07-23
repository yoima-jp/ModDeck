package com.yoima.moddeck.api.option;

import com.yoima.moddeck.api.ConfigText;
import com.yoima.moddeck.api.OptionPresentation;
import com.yoima.moddeck.api.validation.ConfigValidator;
import com.yoima.moddeck.api.validation.ValidationResult;
import java.util.Optional;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/** A typed, mutable configuration value and its UI-independent metadata. */
public abstract class ConfigOption<T> {
    private static final Logger LOGGER = Logger.getLogger(ConfigOption.class.getName());
    private final String id;
    private final ConfigText displayName;
    private final ConfigText description;
    private final T defaultValue;
    private final OptionPresentation presentation;
    private ConfigValidator<T> validator = ConfigValidator.acceptingAll();
    private Consumer<T> changeConsumer = value -> {};
    private Consumer<T> saveConsumer = value -> {};
    private Function<T, List<ConfigText>> tooltipFactory;
    private boolean requiresRestart;
    private boolean editable = true;
    private ConfigText validationError;
    private T value;
    private T savedValue;

    protected ConfigOption(String id, String displayName, String description, T defaultValue,
                           OptionPresentation presentation) {
        this(id, ConfigText.literal(displayName), ConfigText.literal(Objects.requireNonNullElse(description, "")),
                defaultValue, presentation);
    }

    protected ConfigOption(String id, ConfigText displayName, ConfigText description, T defaultValue,
                           OptionPresentation presentation) {
        this.id = requireIdentifier(id, "option id");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.description = Objects.requireNonNullElse(description, ConfigText.empty());
        this.defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
        this.presentation = Objects.requireNonNull(presentation, "presentation");
        this.value = defaultValue;
        this.savedValue = defaultValue;
        this.tooltipFactory = value -> this.description.isEmpty() ? List.of() : List.of(this.description);
    }

    public final String id() { return id; }
    /** @deprecated Prefer {@link #displayNameText()} so translation metadata is retained. */
    @Deprecated public final String displayName() { return displayName.component().getString(); }
    /** @deprecated Prefer {@link #descriptionText()} so translation metadata is retained. */
    @Deprecated public final String description() { return description.component().getString(); }
    public final ConfigText displayNameText() { return displayName; }
    public final ConfigText descriptionText() { return description; }
    public final T defaultValue() { return defaultValue; }
    public final T value() { return value; }
    public final OptionPresentation presentation() { return presentation; }
    public final boolean isRestartRequired() { return requiresRestart; }
    public final boolean editable() { return editable; }
    public final Optional<ConfigText> validationError() { return Optional.ofNullable(validationError); }
    public final List<ConfigText> tooltips() { return List.copyOf(tooltipFactory.apply(value)); }
    public boolean isDirty() { return persistent() && !Objects.equals(value, savedValue); }

    public final void setValue(T value) {
        validationError = null;
        T validated = validate(Objects.requireNonNull(value, "value"));
        ValidationResult result = Objects.requireNonNull(validator.validate(validated), "validator result");
        if (!result.valid()) {
            validationError = result.error().orElseThrow();
            throw new IllegalArgumentException(validationError.component().getString());
        }
        validationError = null;
        if (!Objects.equals(this.value, validated)) {
            this.value = validated;
            try {
                changeConsumer.accept(validated);
            } catch (RuntimeException exception) {
                LOGGER.log(Level.SEVERE, "Config change callback failed for " + id, exception);
            }
        }
    }

    public final boolean trySetValue(T value) {
        try {
            setValue(value);
            return true;
        } catch (RuntimeException exception) {
            if (validationError == null) validationError = ConfigText.translatable("moddeck.validation.invalid");
            return false;
        }
    }

    public final boolean tryDecodeAndSet(Object encodedValue) {
        try {
            return trySetValue(decode(encodedValue));
        } catch (IllegalArgumentException exception) {
            validationError = ConfigText.translatable("moddeck.validation.invalid");
            return false;
        }
    }

    /** Used by storage backends: validates persisted data without reporting it as a user change. */
    public final void loadEncodedValue(Object encodedValue) {
        T decoded = validate(Objects.requireNonNull(decode(encodedValue), "decoded value"));
        ValidationResult result = Objects.requireNonNull(validator.validate(decoded), "validator result");
        if (!result.valid()) throw new IllegalArgumentException(result.error().orElseThrow().component().getString());
        value = decoded;
        savedValue = decoded;
        validationError = null;
    }

    public void reset() {
        setValue(defaultValue);
    }

    public final ConfigOption<T> validateWith(ConfigValidator<T> validator) {
        this.validator = Objects.requireNonNull(validator, "validator");
        setValue(value);
        return this;
    }

    public final ConfigOption<T> onChanged(Consumer<T> consumer) {
        this.changeConsumer = Objects.requireNonNull(consumer, "consumer");
        return this;
    }

    public final ConfigOption<T> onSaved(Consumer<T> consumer) {
        this.saveConsumer = Objects.requireNonNull(consumer, "consumer");
        return this;
    }

    public final ConfigOption<T> tooltip(ConfigText... lines) {
        List<ConfigText> fixed = List.of(lines);
        this.tooltipFactory = value -> fixed;
        return this;
    }

    public final ConfigOption<T> tooltip(Function<T, List<ConfigText>> factory) {
        this.tooltipFactory = Objects.requireNonNull(factory, "factory");
        return this;
    }

    public final ConfigOption<T> requiresRestart() { this.requiresRestart = true; return this; }
    public final ConfigOption<T> editable(boolean editable) { this.editable = editable; return this; }
    public void notifySaved() {
        savedValue = value;
        try {
            saveConsumer.accept(value);
        } catch (RuntimeException exception) {
            LOGGER.log(Level.SEVERE, "Config save callback failed for " + id, exception);
        }
    }
    public boolean persistent() { return true; }

    /** Converts a storage-neutral value and rejects invalid input. */
    public abstract T decode(Object value);

    /** Converts the current value to booleans, numbers, strings, or lists for storage backends. */
    public Object encode() { return value; }

    protected T validate(T value) { return value; }

    private static String requireIdentifier(String value, String label) {
        Objects.requireNonNull(value, label);
        if (!value.matches("[a-z0-9_.-]+")) {
            throw new IllegalArgumentException(label + " must match [a-z0-9_.-]+: " + value);
        }
        return value;
    }
}
