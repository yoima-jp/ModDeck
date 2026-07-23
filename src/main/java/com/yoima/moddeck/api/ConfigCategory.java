package com.yoima.moddeck.api;

import com.yoima.moddeck.api.option.ConfigOption;
import java.util.List;
import java.util.Objects;

public final class ConfigCategory {
    private final String id;
    private final String displayName;
    private final List<ConfigOption<?>> options;

    ConfigCategory(String id, String displayName, List<ConfigOption<?>> options) {
        this.id = Objects.requireNonNull(id, "id");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.options = List.copyOf(options);
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public List<ConfigOption<?>> options() { return options; }
}
