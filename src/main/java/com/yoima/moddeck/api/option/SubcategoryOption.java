package com.yoima.moddeck.api.option;

import com.yoima.moddeck.api.ConfigText;
import com.yoima.moddeck.api.OptionPresentation;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/** Collapsible group whose children participate in validation, reset, search, and persistence. */
public final class SubcategoryOption extends ConfigOption<Boolean> {
    private final List<ConfigOption<?>> children;

    private SubcategoryOption(String id, ConfigText name, ConfigText description,
                              boolean initiallyExpanded, List<ConfigOption<?>> children) {
        super(id, name, description, initiallyExpanded, OptionPresentation.SUBCATEGORY);
        this.children = List.copyOf(children);
        if (this.children.isEmpty()) throw new IllegalArgumentException("Subcategory must contain at least one entry: " + id);
    }

    public static Builder builder(String id, ConfigText name) { return new Builder(id, name); }
    public List<ConfigOption<?>> children() { return children; }

    @Override public Boolean decode(Object value) {
        if (value instanceof Boolean expanded) return expanded;
        throw new IllegalArgumentException("Expected subcategory expansion state for " + id());
    }

    @Override public boolean persistent() { return false; }

    @Override public boolean isDirty() { return children.stream().anyMatch(ConfigOption::isDirty); }

    @Override public void reset() { children.forEach(ConfigOption::reset); }

    @Override public void notifySaved() { children.forEach(ConfigOption::notifySaved); }

    public static final class Builder {
        private final String id;
        private final ConfigText name;
        private ConfigText description = ConfigText.empty();
        private boolean initiallyExpanded = true;
        private final LinkedHashMap<String, ConfigOption<?>> children = new LinkedHashMap<>();

        private Builder(String id, ConfigText name) {
            this.id = Objects.requireNonNull(id, "id");
            this.name = Objects.requireNonNull(name, "name");
        }

        public Builder description(ConfigText description) {
            this.description = Objects.requireNonNull(description, "description");
            return this;
        }
        public Builder initiallyExpanded(boolean expanded) { this.initiallyExpanded = expanded; return this; }
        public Builder add(ConfigOption<?> option) {
            Objects.requireNonNull(option, "option");
            if (children.putIfAbsent(option.id(), option) != null) {
                throw new IllegalArgumentException("Duplicate subcategory option id: " + option.id());
            }
            return this;
        }
        public SubcategoryOption build() {
            return new SubcategoryOption(id, name, description, initiallyExpanded, List.copyOf(children.values()));
        }
    }
}
