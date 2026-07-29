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

    @Test
    void replacesDefinitionAndRemovesItsPreviousRoute() {
        ConfigDefinition original = ConfigDefinition.builder("example_mod")
                .title("Original")
                .route(ConfigRoute.parse("example:old"))
                .category("general", "General")
                .build();
        ConfigDefinition replacement = ConfigDefinition.builder("example_mod")
                .title("Replacement")
                .route(ConfigRoute.parse("example:new"))
                .category("general", "General")
                .build();

        ConfigRegistry.register(original);
        ConfigRegistry.replace(replacement);

        assertSame(replacement, ConfigRegistry.get("example_mod").orElseThrow());
        assertTrue(ConfigRegistry.get(ConfigRoute.parse("example:old")).isEmpty());
        assertSame(replacement, ConfigRegistry.get(ConfigRoute.parse("example:new")).orElseThrow());
    }

    @Test
    void replacementCannotTakeAnotherModsRoute() {
        ConfigDefinition first = ConfigDefinition.builder("first_mod")
                .title("First")
                .route(ConfigRoute.parse("example:first"))
                .category("general", "General")
                .build();
        ConfigDefinition second = ConfigDefinition.builder("second_mod")
                .title("Second")
                .route(ConfigRoute.parse("example:second"))
                .category("general", "General")
                .build();
        ConfigDefinition conflictingReplacement = ConfigDefinition.builder("first_mod")
                .title("Conflict")
                .route(ConfigRoute.parse("example:second"))
                .category("general", "General")
                .build();

        ConfigRegistry.register(first);
        ConfigRegistry.register(second);

        assertThrows(IllegalStateException.class, () -> ConfigRegistry.replace(conflictingReplacement));
        assertSame(first, ConfigRegistry.get("first_mod").orElseThrow());
        assertSame(second, ConfigRegistry.get(ConfigRoute.parse("example:second")).orElseThrow());
    }
}
