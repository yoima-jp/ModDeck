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
}
