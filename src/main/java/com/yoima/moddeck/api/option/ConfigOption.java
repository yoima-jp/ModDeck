package com.yoima.moddeck.api.option;

import com.yoima.moddeck.api.ConfigText;
import com.yoima.moddeck.api.OptionPresentation;
import com.yoima.moddeck.api.ConfigRequirement;
import com.yoima.moddeck.api.validation.ConfigValidator;
import com.yoima.moddeck.api.validation.ValidationResult;
import java.util.Optional;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.function.Supplier;

/** A typed, mutable configuration value and its UI-independent metadata. */
public abstract class ConfigOption<T> {
    private static final Logger LOGGER = Logger.getLogger(ConfigOption.class.getName());
    private final String id;
    private final ConfigText displayName;
    private final ConfigText description;
    private final T initialDefaultValue;
    private Supplier<T> defaultValueSupplier;
    private final OptionPresentation presentation;
    private ConfigValidator<T> validator = ConfigValidator.acceptingAll();
    private Consumer<T> changeConsumer = value -> {};
    private Consumer<T> saveConsumer = value -> {};
    private Function<T, List<ConfigText>> tooltipFactory;
    private Function<T, ConfigText> valueFormatter = value -> ConfigText.literal(String.valueOf(value));
    private ConfigRequirement enableRequirement = () -> true;
    private ConfigRequirement displayRequirement = () -> true;
    private final List<String> searchAliases = new java.util.ArrayList<>();
    private boolean requiresRestart;
    private boolean editable = true;
    private ConfigText validationError;
    private T value;
    private T draftValue;

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
        this.initialDefaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
        this.defaultValueSupplier = () -> initialDefaultValue;
        this.presentation = Objects.requireNonNull(presentation, "presentation");
        this.value = defaultValue;
        this.draftValue = defaultValue;
        this.tooltipFactory = value -> this.description.isEmpty() ? List.of() : List.of(this.description);
    }

    public final String id() { return id; }
    /** @deprecated Prefer {@link #displayNameText()} so translation metadata is retained. */
    @Deprecated public final String displayName() { return displayName.component().getString(); }
    /** @deprecated Prefer {@link #descriptionText()} so translation metadata is retained. */
    @Deprecated public final String description() { return description.component().getString(); }
    public final ConfigText displayNameText() { return displayName; }
    public final ConfigText descriptionText() { return description; }
    public final T defaultValue() {
        return Objects.requireNonNull(defaultValueSupplier.get(), "default value supplier result");
    }
    /** Last value loaded or successfully saved. Runtime consumers should read this value. */
    public final T value() { return value; }
    /** Value currently being edited by a config screen. */
    public final T draftValue() { return draftValue; }
    public final OptionPresentation presentation() { return presentation; }
    public final boolean isRestartRequired() { return requiresRestart; }
    public final boolean editable() { return editable; }
    public final Optional<ConfigText> validationError() { return Optional.ofNullable(validationError); }
    public final List<ConfigText> tooltips() { return List.copyOf(tooltipFactory.apply(draftValue)); }
    public final ConfigText formattedDraftValue() {
        return Objects.requireNonNull(valueFormatter.apply(draftValue), "formatted value");
    }
    public final boolean isEnabled() { return enableRequirement.test(); }
    public final boolean isDisplayed() { return displayRequirement.test(); }
    public final List<String> searchAliases() { return List.copyOf(searchAliases); }
    /** Whether an untyped integration value has a compatible runtime representation for this option. */
    public final boolean isCompatibleValue(Object candidate) {
        return candidate != null && isCompatibleValueType(candidate);
    }
    /** Validates a prospective typed value without changing draft state or validation UI state. */
    public final void validateCandidate(T candidate) {
        validateCandidateValue(candidate, false);
    }
    public boolean isDirty() { return persistent() && !Objects.equals(draftValue, value); }
    public final boolean canResetDraft() { return !Objects.equals(draftValue, defaultValue()); }

    /** Programmatically replaces both the applied and draft value. UI widgets use setDraftValue. */
    public final void setValue(T value) {
        T validated = validateValue(value);
        this.value = validated;
        this.draftValue = validated;
        validationError = null;
    }

    public final void setDraftValue(T value) {
        validationError = null;
        T validated = validateValue(value);
        if (!Objects.equals(this.draftValue, validated)) {
            this.draftValue = validated;
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

    public final boolean trySetDraftValue(T value) {
        try {
            setDraftValue(value);
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

    public final boolean tryDecodeAndSetDraft(Object encodedValue) {
        try {
            return trySetDraftValue(decode(encodedValue));
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
        draftValue = decoded;
        validationError = null;
    }

    public void reset() {
        setDraftValue(defaultValue());
    }

    public void discardChanges() {
        draftValue = value;
        validationError = null;
    }

    public final ConfigOption<T> validateWith(ConfigValidator<T> validator) {
        this.validator = Objects.requireNonNull(validator, "validator");
        validateValue(value);
        validateValue(draftValue);
        return this;
    }

    public final ConfigOption<T> defaultValueFrom(Supplier<T> supplier) {
        this.defaultValueSupplier = Objects.requireNonNull(supplier, "supplier");
        validateValue(defaultValue());
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

    public final ConfigOption<T> formatWith(Function<T, ConfigText> formatter) {
        this.valueFormatter = Objects.requireNonNull(formatter, "formatter");
        return this;
    }

    public final ConfigOption<T> enabledWhen(ConfigRequirement requirement) {
        this.enableRequirement = Objects.requireNonNull(requirement, "requirement");
        return this;
    }

    public final ConfigOption<T> displayedWhen(ConfigRequirement requirement) {
        this.displayRequirement = Objects.requireNonNull(requirement, "requirement");
        return this;
    }

    public final ConfigOption<T> addSearchAliases(String... aliases) {
        for (String alias : aliases) {
            if (alias != null && !alias.isBlank()) searchAliases.add(alias.toLowerCase(java.util.Locale.ROOT));
        }
        return this;
    }

    public final ConfigOption<T> requiresRestart() { this.requiresRestart = true; return this; }
    public final ConfigOption<T> editable(boolean editable) { this.editable = editable; return this; }
    public void notifySaved() {
        value = draftValue;
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

    /** Converts the current draft for an atomic save attempt. */
    public Object encodeDraft() { return draftValue; }

    protected T validate(T value) { return value; }

    /** Custom heterogeneous option types can widen compatibility beyond their default value's class. */
    protected boolean isCompatibleValueType(Object candidate) {
        return value.getClass().isInstance(candidate);
    }

    private T validateValue(T candidate) {
        return validateCandidateValue(candidate, true);
    }

    private T validateCandidateValue(T candidate, boolean reportError) {
        T validated = validate(Objects.requireNonNull(candidate, "value"));
        ValidationResult result = Objects.requireNonNull(validator.validate(validated), "validator result");
        if (!result.valid()) {
            ConfigText error = result.error().orElseThrow();
            if (reportError) validationError = error;
            throw new IllegalArgumentException(error.component().getString());
        }
        return validated;
    }

    private static String requireIdentifier(String value, String label) {
        Objects.requireNonNull(value, label);
        if (!value.matches("[a-z0-9_.-]+")) {
            throw new IllegalArgumentException(label + " must match [a-z0-9_.-]+: " + value);
        }
        return value;
    }
}
