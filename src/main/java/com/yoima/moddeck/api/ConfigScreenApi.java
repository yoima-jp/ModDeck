package com.yoima.moddeck.api;

import com.yoima.moddeck.api.storage.ConfigStorage;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Primary entry point used by other mods to register declarative screens. */
public final class ConfigScreenApi {
    private static final Logger LOGGER = Logger.getLogger(ConfigScreenApi.class.getName());
    private static volatile ConfigStorage storage;

    private ConfigScreenApi() {}

    public static void register(ConfigDefinition definition) {
        ConfigRegistry.register(definition);
        loadStoredValues(definition);
    }

    /** Rebuilds a definition whose choices depend on current client state. */
    public static void registerOrReplace(ConfigDefinition definition) {
        ConfigRegistry.replace(definition);
        loadStoredValues(definition);
    }

    private static void loadStoredValues(ConfigDefinition definition) {
        ConfigStorage currentStorage = storage;
        if (currentStorage != null) {
            try {
                currentStorage.load(definition);
            } catch (IOException | RuntimeException exception) {
                LOGGER.log(Level.WARNING, "Could not load config for " + definition.modId() + "; defaults remain active", exception);
            }
        }
    }

    /** Installs or replaces persistence. Existing definitions are loaded immediately. */
    public static void useStorage(ConfigStorage newStorage) {
        storage = Objects.requireNonNull(newStorage, "newStorage");
        ConfigRegistry.getAll().forEach(definition -> {
            try {
                newStorage.load(definition);
            } catch (IOException | RuntimeException exception) {
                LOGGER.log(Level.WARNING, "Could not load config for " + definition.modId(), exception);
            }
        });
    }

    public static Optional<ConfigStorage> storage() { return Optional.ofNullable(storage); }

    public static ConfigRoute route(String modId) {
        return ConfigRegistry.get(modId).orElseThrow(() -> new IllegalArgumentException(
                "No Mod Deck config is registered for " + modId)).route();
    }

    /** Persists a definition and then invokes its screen- and option-level save callbacks. */
    public static void save(ConfigDefinition definition) throws IOException {
        Objects.requireNonNull(definition, "definition");
        if (!definition.isValid()) throw new IllegalStateException("Cannot save a configuration with validation errors");
        ConfigStorage currentStorage = storage;
        if (currentStorage == null) throw new IOException("No ConfigStorage is installed");
        currentStorage.save(definition);
        definition.notifySaved();
    }
}
