package com.yoima.moddeck.api.storage;

import com.yoima.moddeck.api.ConfigDefinition;
import java.io.IOException;

/** Replaceable persistence backend for registered definitions. */
public interface ConfigStorage {
    void load(ConfigDefinition definition) throws IOException;
    void save(ConfigDefinition definition) throws IOException;

    default void reset(ConfigDefinition definition) throws IOException {
        definition.reset();
        save(definition);
    }
}
