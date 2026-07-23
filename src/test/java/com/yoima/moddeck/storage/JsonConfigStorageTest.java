package com.yoima.moddeck.storage;

import com.yoima.moddeck.api.ConfigDefinition;
import com.yoima.moddeck.api.option.*;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.List;
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

    @Test void roundTripsListsSelectorsColorsAndNestedValues(@TempDir Path directory) throws Exception {
        ValueCodec<String> codec = new ValueCodec<>() {
            @Override public String encode(String value) { return value; }
            @Override public String decode(String value) { return value; }
        };
        ListOption<String> tags = new ListOption<>("tags", com.yoima.moddeck.api.ConfigText.literal("Tags"),
                com.yoima.moddeck.api.ConfigText.empty(), List.of("one"), codec, 0, 8);
        SelectorOption<String> quality = new SelectorOption<>("quality",
                com.yoima.moddeck.api.ConfigText.literal("Quality"), com.yoima.moddeck.api.ConfigText.empty(),
                "balanced", List.of("fast", "balanced"), codec, com.yoima.moddeck.api.ConfigText::literal);
        ColorOption color = new ColorOption("color", com.yoima.moddeck.api.ConfigText.literal("Color"),
                com.yoima.moddeck.api.ConfigText.empty(), 0x112233, false);
        LongOption nested = new LongOption("limit", com.yoima.moddeck.api.ConfigText.literal("Limit"),
                com.yoima.moddeck.api.ConfigText.empty(), 10, 0, 100, 1);
        SubcategoryOption group = SubcategoryOption.builder("advanced",
                com.yoima.moddeck.api.ConfigText.literal("Advanced")).add(nested).build();
        ConfigDefinition source = ConfigDefinition.builder("extended_mod").category("general", "General")
                .addOption(tags).addOption(quality).addOption(color).addOption(group).build();
        tags.setValue(List.of("two", "three"));
        quality.setValue("fast");
        color.setValue(0xAABBCC);
        nested.setValue(42L);
        JsonConfigStorage storage = new JsonConfigStorage(directory);
        storage.save(source);

        ListOption<String> loadedTags = new ListOption<>("tags", com.yoima.moddeck.api.ConfigText.literal("Tags"),
                com.yoima.moddeck.api.ConfigText.empty(), List.of(), codec, 0, 8);
        SelectorOption<String> loadedQuality = new SelectorOption<>("quality",
                com.yoima.moddeck.api.ConfigText.literal("Quality"), com.yoima.moddeck.api.ConfigText.empty(),
                "balanced", List.of("fast", "balanced"), codec, com.yoima.moddeck.api.ConfigText::literal);
        ColorOption loadedColor = new ColorOption("color", com.yoima.moddeck.api.ConfigText.literal("Color"),
                com.yoima.moddeck.api.ConfigText.empty(), 0, false);
        LongOption loadedNested = new LongOption("limit", com.yoima.moddeck.api.ConfigText.literal("Limit"),
                com.yoima.moddeck.api.ConfigText.empty(), 10, 0, 100, 1);
        ConfigDefinition loaded = ConfigDefinition.builder("extended_mod").category("general", "General")
                .addOption(loadedTags).addOption(loadedQuality).addOption(loadedColor)
                .addOption(SubcategoryOption.builder("advanced", com.yoima.moddeck.api.ConfigText.literal("Advanced"))
                        .add(loadedNested).build()).build();
        storage.load(loaded);

        assertEquals(List.of("two", "three"), loadedTags.value());
        assertEquals("fast", loadedQuality.value());
        assertEquals(0xAABBCC, loadedColor.value());
        assertEquals(42L, loadedNested.value());
    }

    private static ConfigDefinition definition() {
        return ConfigDefinition.builder("example_mod").title("Example Mod").category("general", "General")
                .booleanOption("enabled", "Enabled", true).integerOption("volume", "Volume", 50, 0, 100)
                .doubleOption("opacity", "Opacity", 0.5, 0, 1).stringOption("name", "Name", "Player")
                .enumOption("mode", "Mode", Mode.SIMPLE, Mode.class).build();
    }
}
