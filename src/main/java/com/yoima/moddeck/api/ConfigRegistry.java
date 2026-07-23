package com.yoima.moddeck.api;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe central registry. Duplicate mod IDs are rejected, never overwritten. */
public final class ConfigRegistry {
    private static final Map<String, ConfigDefinition> DEFINITIONS = new ConcurrentHashMap<>();

    private ConfigRegistry() {}

    public static void register(ConfigDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        if (DEFINITIONS.putIfAbsent(definition.modId(), definition) != null) {
            throw new IllegalStateException("A config definition is already registered for " + definition.modId());
        }
    }

    public static Optional<ConfigDefinition> get(String modId) {
        return Optional.ofNullable(DEFINITIONS.get(modId));
    }

    public static List<ConfigDefinition> getAll() {
        return DEFINITIONS.values().stream().sorted(Comparator.comparing(ConfigDefinition::title)).toList();
    }

    static void clearForTests() { DEFINITIONS.clear(); }
}
