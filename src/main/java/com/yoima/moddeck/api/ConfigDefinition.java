package com.yoima.moddeck.api;

import com.yoima.moddeck.api.option.*;
import java.util.*;

/** Immutable screen structure containing mutable typed option values. */
public final class ConfigDefinition {
    private final String modId;
    private final String title;
    private final String description;
    private final List<ConfigCategory> categories;

    private ConfigDefinition(String modId, String title, String description, List<ConfigCategory> categories) {
        this.modId = modId;
        this.title = title;
        this.description = description;
        this.categories = List.copyOf(categories);
    }

    public static Builder builder(String modId) { return new Builder(modId); }
    public String modId() { return modId; }
    public String title() { return title; }
    public String description() { return description; }
    public List<ConfigCategory> categories() { return categories; }

    public Optional<ConfigOption<?>> option(String categoryId, String optionId) {
        return categories.stream().filter(category -> category.id().equals(categoryId))
                .flatMap(category -> category.options().stream())
                .filter(option -> option.id().equals(optionId)).findFirst();
    }

    public void reset() { categories.forEach(category -> category.options().forEach(ConfigOption::reset)); }

    public static final class Builder {
        private final String modId;
        private String title;
        private String description = "";
        private final LinkedHashMap<String, CategoryBuilder> categories = new LinkedHashMap<>();
        private CategoryBuilder currentCategory;

        private Builder(String modId) {
            if (modId == null || !modId.matches("[a-z0-9_.-]+")) {
                throw new IllegalArgumentException("modId must match [a-z0-9_.-]+");
            }
            this.modId = modId;
            this.title = modId;
        }

        public Builder title(String title) { this.title = Objects.requireNonNull(title, "title"); return this; }
        public Builder description(String description) {
            this.description = Objects.requireNonNull(description, "description");
            return this;
        }

        public Builder category(String id, String displayName) {
            if (categories.containsKey(id)) throw new IllegalArgumentException("Duplicate category id: " + id);
            currentCategory = new CategoryBuilder(id, displayName);
            categories.put(id, currentCategory);
            return this;
        }

        public Builder booleanOption(String id, String name, boolean defaultValue) {
            return booleanOption(id, name, "", defaultValue);
        }
        public Builder booleanOption(String id, String name, String description, boolean defaultValue) {
            return add(new BooleanOption(id, name, description, defaultValue));
        }
        public Builder integerOption(String id, String name, int defaultValue, int min, int max) {
            return integerOption(id, name, "", defaultValue, min, max, 1);
        }
        public Builder integerOption(String id, String name, String description, int defaultValue, int min, int max, int step) {
            return add(new IntegerOption(id, name, description, defaultValue, min, max, step));
        }
        public Builder doubleOption(String id, String name, double defaultValue, double min, double max) {
            return doubleOption(id, name, "", defaultValue, min, max, 0.01);
        }
        public Builder doubleOption(String id, String name, String description, double defaultValue, double min, double max, double step) {
            return add(new DoubleOption(id, name, description, defaultValue, min, max, step));
        }
        public Builder stringOption(String id, String name, String defaultValue) {
            return stringOption(id, name, "", defaultValue, 256);
        }
        public Builder stringOption(String id, String name, String description, String defaultValue, int maximumLength) {
            return add(new StringOption(id, name, description, defaultValue, maximumLength));
        }
        public <E extends Enum<E>> Builder enumOption(String id, String name, E defaultValue, Class<E> type) {
            return enumOption(id, name, "", defaultValue, type);
        }
        public <E extends Enum<E>> Builder enumOption(String id, String name, String description, E defaultValue, Class<E> type) {
            return add(new EnumOption<>(id, name, description, defaultValue, type));
        }

        public ConfigDefinition build() {
            if (categories.isEmpty()) throw new IllegalStateException("At least one category is required");
            return new ConfigDefinition(modId, title, description,
                    categories.values().stream().map(CategoryBuilder::build).toList());
        }

        private Builder add(ConfigOption<?> option) {
            if (currentCategory == null) throw new IllegalStateException("Call category() before adding options");
            currentCategory.add(option);
            return this;
        }
    }

    private static final class CategoryBuilder {
        private final String id;
        private final String displayName;
        private final LinkedHashMap<String, ConfigOption<?>> options = new LinkedHashMap<>();

        private CategoryBuilder(String id, String displayName) {
            if (id == null || !id.matches("[a-z0-9_.-]+")) throw new IllegalArgumentException("Invalid category id: " + id);
            this.id = id;
            this.displayName = Objects.requireNonNull(displayName, "displayName");
        }
        private void add(ConfigOption<?> option) {
            if (options.putIfAbsent(option.id(), option) != null) {
                throw new IllegalArgumentException("Duplicate option id in category " + id + ": " + option.id());
            }
        }
        private ConfigCategory build() { return new ConfigCategory(id, displayName, List.copyOf(options.values())); }
    }
}
