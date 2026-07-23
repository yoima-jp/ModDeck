package com.yoima.moddeck.api.option;

import com.yoima.moddeck.api.OptionPresentation;

public final class StringOption extends ConfigOption<String> {
    private final int maximumLength;

    public StringOption(String id, String name, String description, String defaultValue, int maximumLength) {
        super(id, name, description, defaultValue, OptionPresentation.TEXT_FIELD);
        if (maximumLength < 1 || defaultValue.length() > maximumLength) {
            throw new IllegalArgumentException("Invalid maximum length for " + id);
        }
        this.maximumLength = maximumLength;
    }

    public int maximumLength() { return maximumLength; }

    @Override public String decode(Object value) {
        if (!(value instanceof String text)) throw new IllegalArgumentException("Expected string for " + id());
        return validate(text);
    }

    @Override protected String validate(String value) {
        if (value.length() > maximumLength) throw new IllegalArgumentException("Value is too long for " + id());
        return value;
    }
}
