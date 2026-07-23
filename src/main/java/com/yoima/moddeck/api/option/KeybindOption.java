package com.yoima.moddeck.api.option;

import com.yoima.moddeck.api.ConfigText;
import com.yoima.moddeck.api.OptionPresentation;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Stores Minecraft key identifiers without loading client-only input classes. */
public final class KeybindOption extends ConfigOption<String> {
    public static final String UNBOUND_KEY = "key.keyboard.unknown";

    public enum InputType { KEYBOARD, MOUSE }

    private EnumSet<InputType> allowedInputs;
    private boolean allowUnbound;

    public KeybindOption(String id, ConfigText name, ConfigText description, String defaultKey) {
        this(id, name, description, defaultKey, Set.of(InputType.KEYBOARD), true);
    }

    public KeybindOption(String id, ConfigText name, ConfigText description, String defaultKey,
                         Set<InputType> allowedInputs, boolean allowUnbound) {
        super(id, name, description, requireKey(defaultKey), OptionPresentation.KEYBIND);
        this.allowedInputs = copyInputs(allowedInputs);
        this.allowUnbound = allowUnbound;
        requireAllowed(defaultKey);
    }

    public Set<InputType> allowedInputs() { return Set.copyOf(allowedInputs); }
    public boolean allows(InputType inputType) { return allowedInputs.contains(inputType); }
    public boolean allowsUnbound() { return allowUnbound; }
    public boolean isUnbound() { return UNBOUND_KEY.equals(value()); }

    public KeybindOption allowedInputs(InputType first, InputType... remaining) {
        Objects.requireNonNull(first, "first");
        EnumSet<InputType> next = EnumSet.of(first, remaining);
        InputType currentType = inputType(value());
        if (!isUnbound() && !next.contains(currentType)) {
            throw new IllegalStateException("Current key type would no longer be allowed for " + id());
        }
        allowedInputs = next;
        return this;
    }

    public KeybindOption allowUnbound(boolean allowed) {
        if (!allowed && isUnbound()) {
            throw new IllegalStateException("Current key is unbound for " + id());
        }
        allowUnbound = allowed;
        return this;
    }

    @Override public String decode(Object value) {
        if (!(value instanceof String key)) throw new IllegalArgumentException("Expected key name for " + id());
        return requireAllowed(key);
    }

    @Override protected String validate(String value) { return requireAllowed(value); }

    private String requireAllowed(String value) {
        String key = requireKey(value);
        if (UNBOUND_KEY.equals(key)) {
            if (!allowUnbound) throw new IllegalArgumentException("Unbound input is not allowed for " + id());
            return key;
        }
        InputType type = inputType(key);
        if (!allowedInputs.contains(type)) {
            throw new IllegalArgumentException(type + " input is not allowed for " + id());
        }
        return key;
    }

    private static InputType inputType(String key) {
        if (key.startsWith("key.keyboard.")) return InputType.KEYBOARD;
        if (key.startsWith("key.mouse.")) return InputType.MOUSE;
        throw new IllegalArgumentException("Unsupported Minecraft input identifier: " + key);
    }

    private static String requireKey(String value) {
        if (value == null || !value.matches("key\\.(keyboard|mouse)\\.[a-z0-9_.-]+")) {
            throw new IllegalArgumentException("Invalid Minecraft input identifier: " + value);
        }
        return value;
    }

    private static EnumSet<InputType> copyInputs(Set<InputType> inputs) {
        Objects.requireNonNull(inputs, "allowedInputs");
        if (inputs.isEmpty()) throw new IllegalArgumentException("At least one input type is required");
        return EnumSet.copyOf(inputs);
    }
}
