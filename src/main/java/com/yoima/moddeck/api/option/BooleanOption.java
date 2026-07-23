package com.yoima.moddeck.api.option;

import com.yoima.moddeck.api.OptionPresentation;

public final class BooleanOption extends ConfigOption<Boolean> {
    public BooleanOption(String id, String name, String description, boolean defaultValue) {
        super(id, name, description, defaultValue, OptionPresentation.TOGGLE);
    }

    @Override public Boolean decode(Object value) {
        if (value instanceof Boolean result) return result;
        if (value instanceof String text && (text.equalsIgnoreCase("true") || text.equalsIgnoreCase("false"))) {
            return Boolean.parseBoolean(text);
        }
        throw new IllegalArgumentException("Expected boolean for " + id());
    }
}
