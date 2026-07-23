package com.yoima.moddeck.storage;

import com.yoima.moddeck.api.ConfigDefinition;
import com.yoima.moddeck.api.option.*;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class JsonConfigStorageTest {
    enum Mode { SIMPLE, COMPACT }

    @Test void roundTripsAllTypes(@TempDir Path directory) throws Exception {
        JsonConfigStorage storage = new JsonConfigStorage(directory);
        ConfigDefinition source = definition();
        ((BooleanOption) source.option("general", "enabled").orElseThrow()).setValue(false);
        ((IntegerOption) source.option("general", "volume").orElseThrow()).setValue(75);
        ((DoubleOption) source.option("general", "opacity").orElseThrow()).setValue(0.8);
        ((StringOption) source.option("general", "name").orElseThrow()).setValue("Alex");
        @SuppressWarnings("unchecked") EnumOption<Mode> mode = (EnumOption<Mode>) source.option("general", "mode").orElseThrow();
        mode.setValue(Mode.COMPACT);
        storage.save(source);

        ConfigDefinition loaded = definition();
        storage.load(loaded);
        assertEquals(false, loaded.option("general", "enabled").orElseThrow().value());
        assertEquals(75, loaded.option("general", "volume").orElseThrow().value());
        assertEquals(0.8, loaded.option("general", "opacity").orElseThrow().value());
        assertEquals("Alex", loaded.option("general", "name").orElseThrow().value());
        assertEquals(Mode.COMPACT, loaded.option("general", "mode").orElseThrow().value());
    }

    @Test void rejectsMalformedJsonWithoutChangingDefaults(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("example_mod.json"), "{not-json", StandardCharsets.UTF_8);
        ConfigDefinition definition = definition();

        assertThrows(java.io.IOException.class, () -> new JsonConfigStorage(directory).load(definition));
        assertEquals(true, definition.option("general", "enabled").orElseThrow().value());
        assertEquals(50, definition.option("general", "volume").orElseThrow().value());
    }

    private static ConfigDefinition definition() {
        return ConfigDefinition.builder("example_mod").title("Example Mod").category("general", "General")
                .booleanOption("enabled", "Enabled", true).integerOption("volume", "Volume", 50, 0, 100)
                .doubleOption("opacity", "Opacity", 0.5, 0, 1).stringOption("name", "Name", "Player")
                .enumOption("mode", "Mode", Mode.SIMPLE, Mode.class).build();
    }
}
