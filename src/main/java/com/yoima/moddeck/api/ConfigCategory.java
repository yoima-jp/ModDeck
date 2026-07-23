package com.yoima.moddeck.api;

import com.yoima.moddeck.api.option.ConfigOption;
import java.util.List;
import java.util.Objects;

public final class ConfigCategory {
    private final String id;
    private final ConfigText displayName;
    private final int order;
    private final List<ConfigOption<?>> options;

    ConfigCategory(String id, ConfigText displayName, int order, List<ConfigOption<?>> options) {
        this.id = Objects.requireNonNull(id, "id");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.order = order;
        this.options = List.copyOf(options);
    }

    public String id() { return id; }
    /** @deprecated Prefer {@link #displayNameText()}. */
    @Deprecated public String displayName() { return displayName.component().getString(); }
    public ConfigText displayNameText() { return displayName; }
    public int order() { return order; }
    public List<ConfigOption<?>> options() { return options; }
}
