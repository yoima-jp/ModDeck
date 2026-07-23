package com.yoima.moddeck.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConfigRegistryTest {
    @BeforeEach
    void clearRegistry() {
        ConfigRegistry.clearForTests();
    }

    @Test
    void registersAndRetrievesDefinitionsWithoutOverwritingDuplicates() {
        ConfigDefinition definition = ConfigDefinition.builder("example_mod")
                .title("Example Mod")
                .category("general", "General")
                .booleanOption("enabled", "Enabled", true)
                .build();

        ConfigRegistry.register(definition);

        assertSame(definition, ConfigRegistry.get("example_mod").orElseThrow());
        assertSame(definition, ConfigRegistry.get(ConfigRoute.parse("moddeck:config/example_mod")).orElseThrow());
        assertEquals(1, ConfigRegistry.getAll().size());
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> ConfigRegistry.register(definition));
        assertTrue(exception.getMessage().contains("example_mod"));
        assertSame(definition, ConfigRegistry.get("example_mod").orElseThrow());
    }
}
