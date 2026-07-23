package com.yoima.moddeck.api;

import com.yoima.moddeck.api.option.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConfigDefinitionTest {
    enum Mode { SIMPLE, DETAILED }

    @Test void buildsEveryMvpTypeAndResetsValues() {
        ConfigDefinition definition = ConfigDefinition.builder("example_mod").title("Example Mod")
                .description("Example description")
                .category("general", "General")
                .booleanOption("enabled", "Enabled", true)
                .integerOption("volume", "Volume", 50, 0, 100)
                .doubleOption("opacity", "Opacity", 0.5, 0, 1)
                .stringOption("name", "Name", "Player")
                .enumOption("mode", "Mode", Mode.SIMPLE, Mode.class).build();
        assertEquals(5, definition.categories().getFirst().options().size());
        assertEquals("Example description", definition.description());
        IntegerOption volume = (IntegerOption) definition.option("general", "volume").orElseThrow();
        volume.setValue(80);
        definition.reset();
        assertEquals(50, volume.value());
    }

    @Test void rejectsDuplicateOptionIds() {
        var builder = ConfigDefinition.builder("example_mod").category("general", "General")
                .booleanOption("enabled", "Enabled", true);
        assertThrows(IllegalArgumentException.class,
                () -> builder.booleanOption("enabled", "Again", false));
    }

    @Test void decimalStepDoesNotExposeFloatingPointArtifacts() {
        DoubleOption option = new DoubleOption("opacity", "Opacity", "", 0.8, 0, 1, 0.05);
        option.setValue(0.30000000000000004);
        assertEquals(0.3, option.value());
    }
}
