package com.yoima.moddeck.api;

import java.util.*;

/**
 * Immutable snapshot of named configuration values that can be applied to a {@link ConfigDefinition}
 * as a batch of draft changes. A preset does not save; it only updates draft values, leaving the
 * existing validation, change-callback, and dirty-state machinery intact.
 */
public final class ConfigPreset {
    private final String id;
    private final ConfigText displayText;
    private final ConfigText description;
    private final List<Entry> entries;

    private ConfigPreset(String id, ConfigText displayText, ConfigText description, List<Entry> entries) {
        this.id = id;
        this.displayText = displayText;
        this.description = description;
        this.entries = List.copyOf(entries);
    }

    public String id() { return id; }
    public ConfigText displayText() { return displayText; }
    public ConfigText description() { return description; }
    public List<Entry> entries() { return entries; }

    public static Builder builder(String id, ConfigText displayText) { return new Builder(id, displayText); }
    public static Builder builder(String id, String displayText) { return builder(id, ConfigText.literal(displayText)); }
    public static Builder builderKey(String id, String displayKey) {
        return builder(id, ConfigText.translatable(displayKey));
    }

    /**
     * A single value assignment within a preset. {@code categoryId} identifies a top-level category
     * in the owning {@link ConfigDefinition}; {@code optionId} identifies an option that may be nested
     * inside a subcategory. {@code value} is the typed Java object that {@code ConfigOption.setDraftValue}
     * would accept — not the encoded/storage form.
     */
    public record Entry(String categoryId, String optionId, Object value) {
        public Entry {
            categoryId = requireIdentifier(categoryId, "category id");
            optionId = requireIdentifier(optionId, "option id");
            Objects.requireNonNull(value, "value");
        }
    }

    public static final class Builder {
        private final String id;
        private final ConfigText displayText;
        private ConfigText description = ConfigText.empty();
        private final List<Entry> entries = new ArrayList<>();
        // Tracks categoryId:optionId pairs to reject a preset that sets the same option twice.
        private final Set<String> targets = new HashSet<>();

        private Builder(String id, ConfigText displayText) {
            this.id = requireIdentifier(id, "preset id");
            this.displayText = Objects.requireNonNull(displayText, "displayText");
        }

        public Builder description(ConfigText description) {
            this.description = Objects.requireNonNull(description, "description");
            return this;
        }

        public Builder set(String categoryId, String optionId, Object value) {
            String key = categoryId + ":" + optionId;
            if (!targets.add(key)) {
                throw new IllegalArgumentException("Duplicate preset target: " + key);
            }
            entries.add(new Entry(categoryId, optionId, value));
            return this;
        }

        public ConfigPreset build() {
            if (entries.isEmpty()) {
                throw new IllegalStateException("Preset must contain at least one entry: " + id);
            }
            return new ConfigPreset(id, displayText, description, entries);
        }
    }

    private static String requireIdentifier(String value, String label) {
        Objects.requireNonNull(value, label);
        if (!value.matches("[a-z0-9_.-]+")) {
            throw new IllegalArgumentException(label + " must match [a-z0-9_.-]+: " + value);
        }
        return value;
    }
}
