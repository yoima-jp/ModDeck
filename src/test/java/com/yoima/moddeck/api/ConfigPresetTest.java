package com.yoima.moddeck.api;

import com.yoima.moddeck.api.option.*;
import com.yoima.moddeck.api.validation.ValidationResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConfigPresetTest {

    private static ConfigDefinition.Builder baseBuilder() {
        return ConfigDefinition.builder("preset_mod")
                .category("general", "General")
                .booleanOption("enabled", "Enabled", true)
                .integerOption("volume", "Volume", 50, 0, 100)
                .stringOption("name", "Name", "Player")
                .enumOption("mode", "Mode", ConfigDefinitionTest.Mode.SIMPLE, ConfigDefinitionTest.Mode.class)
                .category("display", "Display")
                .doubleOption("opacity", "Opacity", 0.5, 0, 1)
                .colorOption("accent", ConfigText.literal("Accent"), ConfigText.empty(), 0xFF0000, false)
                .keybindOption("hotkey", ConfigText.literal("Hotkey"), ConfigText.empty(), "key.keyboard.g");
    }

    @Test void registersAndExposesPresetMetadata() {
        ConfigPreset preset = ConfigPreset.builder("performance", "Performance")
                .description(ConfigText.literal("High FPS settings"))
                .set("general", "enabled", false)
                .set("general", "volume", 30)
                .build();

        ConfigDefinition definition = baseBuilder().preset(preset).build();

        assertEquals(1, definition.presets().size());
        assertSame(preset, definition.presets().getFirst());
        assertSame(preset, definition.preset("performance").orElseThrow());
        assertEquals("Performance", preset.displayText().value());
        assertEquals("High FPS settings", preset.description().value());
        assertEquals(2, preset.entries().size());
        assertEquals("general", preset.entries().get(0).categoryId());
        assertEquals("enabled", preset.entries().get(0).optionId());
        assertEquals(false, preset.entries().get(0).value());
    }

    @Test void applyPresetUpdatesDraftValuesWithoutSaving() {
        ConfigPreset preset = ConfigPreset.builder("quiet", "Quiet Mode")
                .set("general", "enabled", false)
                .set("general", "volume", 10)
                .set("display", "opacity", 0.2)
                .build();
        ConfigDefinition definition = baseBuilder().preset(preset).build();

        definition.applyPreset("quiet");

        BooleanOption enabled = (BooleanOption) definition.option("general", "enabled").orElseThrow();
        IntegerOption volume = (IntegerOption) definition.option("general", "volume").orElseThrow();
        DoubleOption opacity = (DoubleOption) definition.option("display", "opacity").orElseThrow();

        assertEquals(false, enabled.draftValue());
        assertEquals(10, volume.draftValue());
        assertEquals(0.2, opacity.draftValue());
        // Applied values are the new draft; committed value is untouched.
        assertTrue(definition.isDirty());
        assertEquals(true, enabled.value());
        assertEquals(50, volume.value());
        assertEquals(0.5, opacity.value());
    }

    @Test void applyPresetInvokesChangeCallbacks() {
        AtomicInteger changes = new AtomicInteger();
        AtomicInteger saved = new AtomicInteger();
        IntegerOption volume = new IntegerOption("volume", ConfigText.literal("Volume"),
                ConfigText.empty(), 50, 0, 100, 1);
        volume.onChanged(value -> changes.incrementAndGet())
                .onSaved(value -> saved.incrementAndGet());
        ConfigDefinition definition = ConfigDefinition.builder("callback_mod")
                .category("general", "General").addOption(volume)
                .preset(ConfigPreset.builder("loud", "Loud").set("general", "volume", 90).build())
                .build();

        definition.applyPreset("loud");
        assertEquals(90, volume.draftValue());
        assertEquals(1, changes.get());
        // Presets must not trigger save callbacks.
        assertEquals(0, saved.get());
        assertTrue(definition.isDirty());
    }

    @Test void applyPresetForInvalidValueFailsFast() {
        ConfigPreset preset = ConfigPreset.builder("bad_volume", "Bad Volume")
                .set("general", "volume", 999)
                .build();
        // Value validation happens at apply time via setDraftValue, not at build time,
        // because preset values are typed Java objects, not the encoded form decode expects.
        ConfigDefinition definition = baseBuilder().preset(preset).build();
        assertThrows(IllegalArgumentException.class, () -> definition.applyPreset("bad_volume"));
    }

    @Test void invalidPresetDoesNotPartiallyApplyEarlierEntries() {
        ConfigPreset preset = ConfigPreset.builder("partially_bad", "Partially bad")
                .set("general", "enabled", false)
                .set("general", "volume", 999)
                .build();
        ConfigDefinition definition = baseBuilder().preset(preset).build();

        assertThrows(IllegalArgumentException.class, () -> definition.applyPreset("partially_bad"));

        assertEquals(true, ((BooleanOption) definition.option("general", "enabled")
                .orElseThrow()).draftValue());
        assertFalse(definition.isDirty());
    }

    @Test void applyPresetForIncompatibleTypeFailsFast() {
        ConfigPreset preset = ConfigPreset.builder("wrong_type", "Wrong Type")
                .set("general", "volume", "not-an-integer")
                .build();
        ConfigDefinition definition = baseBuilder().preset(preset).build();
        assertThrows(IllegalArgumentException.class, () -> definition.applyPreset("wrong_type"));
    }

    @Test void applyPresetRejectsWrongTypeForOptionWithoutCustomValidation() {
        ConfigPreset preset = ConfigPreset.builder("wrong_boolean", "Wrong Boolean")
                .set("general", "enabled", 1)
                .build();
        ConfigDefinition definition = baseBuilder().preset(preset).build();

        assertThrows(IllegalArgumentException.class, () -> definition.applyPreset("wrong_boolean"));
        assertEquals(true, ((BooleanOption) definition.option("general", "enabled")
                .orElseThrow()).draftValue());
    }

    @Test void duplicatePresetIdIsRejected() {
        ConfigPreset first = ConfigPreset.builder("dup", "First")
                .set("general", "enabled", false).build();
        ConfigPreset second = ConfigPreset.builder("dup", "Second")
                .set("general", "volume", 20).build();

        assertThrows(IllegalArgumentException.class,
                () -> baseBuilder().preset(first).preset(second).build());
    }

    @Test void presetTargetingUnknownCategoryFailsAtBuild() {
        ConfigPreset preset = ConfigPreset.builder("ghost_category", "Ghost Category")
                .set("nonexistent", "enabled", true).build();
        assertThrows(IllegalArgumentException.class, () -> baseBuilder().preset(preset).build());
    }

    @Test void presetTargetingUnknownOptionFailsAtBuild() {
        ConfigPreset preset = ConfigPreset.builder("ghost_option", "Ghost Option")
                .set("general", "ghost", true).build();
        assertThrows(IllegalArgumentException.class, () -> baseBuilder().preset(preset).build());
    }

    @Test void presetTargetingNonPersistentOptionFailsAtBuild() {
        ConfigPreset preset = ConfigPreset.builder("action_preset", "Action")
                .set("general", "reload", true).build();
        assertThrows(IllegalArgumentException.class, () -> baseBuilder()
                .buttonOption("reload", "Reload", () -> {})
                .preset(preset)
                .build());
    }

    @Test void applyUnknownPresetIdThrows() {
        ConfigDefinition definition = baseBuilder().build();
        assertThrows(IllegalArgumentException.class, () -> definition.applyPreset("nope"));
    }

    @Test void presetMetadataIsImmutable() {
        ConfigPreset preset = ConfigPreset.builder("imm", "Immutable")
                .set("general", "enabled", false).build();

        assertThrows(UnsupportedOperationException.class, () -> preset.entries().clear());
        assertEquals(1, preset.entries().size());
    }

    @Test void presetEntryRejectsNullValues() {
        assertThrows(NullPointerException.class,
                () -> ConfigPreset.builder("null_val", "Null").set("general", "enabled", null));
    }

    @Test void presetBuilderRejectsEmptyPreset() {
        assertThrows(IllegalStateException.class,
                () -> ConfigPreset.builder("empty", "Empty").build());
    }

    @Test void presetBuilderRejectsDuplicateTargetWithinPreset() {
        assertThrows(IllegalArgumentException.class,
                () -> ConfigPreset.builder("dup_target", "Dup Target")
                        .set("general", "enabled", false)
                        .set("general", "enabled", true));
    }

    @Test void presetIdMustMatchIdentifierPattern() {
        assertThrows(IllegalArgumentException.class,
                () -> ConfigPreset.builder("UPPER", "Bad Id").set("general", "enabled", false).build());
    }

    @Test void presetEntryIdsMustMatchIdentifierPattern() {
        assertThrows(IllegalArgumentException.class,
                () -> new ConfigPreset.Entry("Bad Category", "enabled", false));
        assertThrows(IllegalArgumentException.class,
                () -> new ConfigPreset.Entry("general", "Bad Option", false));
    }

    @Test void listPresetAcceptsAnyCompatibleListImplementation() {
        ValueCodec<String> codec = new ValueCodec<>() {
            @Override public String encode(String value) { return value; }
            @Override public String decode(String value) { return value; }
        };
        ListOption<String> names = new ListOption<>("names", ConfigText.literal("Names"),
                ConfigText.empty(), List.of("Alex"), codec, 1, 4);
        ConfigPreset preset = ConfigPreset.builder("friends", "Friends")
                .set("general", "names", new ArrayList<>(List.of("Steve", "Alex")))
                .build();
        ConfigDefinition definition = ConfigDefinition.builder("list_preset_mod")
                .category("general", "General")
                .addOption(names)
                .preset(preset)
                .build();

        definition.applyPreset("friends");

        assertEquals(List.of("Steve", "Alex"), names.draftValue());
        assertTrue(definition.isDirty());
    }

    @Test void applyPresetAcrossMultipleCategories() {
        ConfigPreset preset = ConfigPreset.builder("custom", "Custom")
                .set("general", "enabled", false)
                .set("general", "volume", 75)
                .set("general", "mode", ConfigDefinitionTest.Mode.DETAILED)
                .set("display", "opacity", 0.9)
                .set("display", "accent", 0x00FF00)
                .build();
        ConfigDefinition definition = baseBuilder().preset(preset).build();

        definition.applyPreset("custom");

        assertEquals(false, ((BooleanOption) definition.option("general", "enabled").orElseThrow()).draftValue());
        assertEquals(75, ((IntegerOption) definition.option("general", "volume").orElseThrow()).draftValue());
        assertEquals(ConfigDefinitionTest.Mode.DETAILED,
                ((EnumOption<?>) definition.option("general", "mode").orElseThrow()).draftValue());
        assertEquals(0.9, ((DoubleOption) definition.option("display", "opacity").orElseThrow()).draftValue());
        assertEquals(0x00FF00, ((ColorOption) definition.option("display", "accent").orElseThrow()).draftValue());
    }

    @Test void presetTargetsNestedSubcategoryOption() {
        StringOption child = new StringOption("child", ConfigText.literal("Child"),
                ConfigText.empty(), "default", 32);
        SubcategoryOption group = SubcategoryOption.builder("group", ConfigText.literal("Group"))
                .add(child).build();
        ConfigPreset preset = ConfigPreset.builder("nested_preset", "Nested")
                .set("general", "child", "preset_value").build();

        ConfigDefinition definition = ConfigDefinition.builder("nested_mod")
                .category("general", "General").addOption(group)
                .preset(preset).build();

        definition.applyPreset("nested_preset");
        assertEquals("preset_value", child.draftValue());
        assertTrue(definition.isDirty());
    }

    @Test void duplicateNestedOptionIdsAreRejectedAsAmbiguous() {
        StringOption child = new StringOption("duplicate", ConfigText.literal("Child"),
                ConfigText.empty(), "child", 32);
        SubcategoryOption group = SubcategoryOption.builder("group", ConfigText.literal("Group"))
                .add(child).build();

        assertThrows(IllegalArgumentException.class, () -> ConfigDefinition.builder("ambiguous_mod")
                .category("general", "General")
                .stringOption("duplicate", "Top level", "top")
                .addOption(group)
                .build());
    }

    @Test void applyPresetParticipatesInValidation() {
        AtomicInteger changes = new AtomicInteger();
        IntegerOption count = new IntegerOption("count", ConfigText.literal("Count"),
                ConfigText.empty(), 2, 0, 10, 1);
        count.validateWith(value -> value % 2 == 0 ? ValidationResult.success()
                        : ValidationResult.invalid(ConfigText.literal("Even values only")))
                .onChanged(value -> changes.incrementAndGet());
        ConfigDefinition definition = ConfigDefinition.builder("validation_mod")
                .category("general", "General").addOption(count)
                .preset(ConfigPreset.builder("odd_preset", "Odd")
                        .set("general", "count", 3).build())
                .build();

        // Validation fires at apply time via setDraftValue: the odd value is rejected and the
        // change callback is not invoked because setDraftValue never reaches the assignment.
        assertThrows(IllegalArgumentException.class, () -> definition.applyPreset("odd_preset"));
        assertEquals(2, count.draftValue());
        assertEquals(0, changes.get());
    }

    @Test void applyPresetDoesNotAutoSave() {
        AtomicInteger saves = new AtomicInteger();
        BooleanOption enabled = new BooleanOption("enabled", ConfigText.literal("Enabled"),
                ConfigText.empty(), true);
        ConfigDefinition definition = ConfigDefinition.builder("no_save_mod")
                .category("general", "General").addOption(enabled)
                .onSave(saves::incrementAndGet)
                .preset(ConfigPreset.builder("disable", "Disable")
                        .set("general", "enabled", false).build())
                .build();

        definition.applyPreset("disable");
        assertEquals(false, enabled.draftValue());
        assertTrue(definition.isDirty());
        assertEquals(0, saves.get());

        // Only explicit save commits the value.
        definition.notifySaved();
        assertFalse(definition.isDirty());
        assertEquals(false, enabled.value());
        assertEquals(1, saves.get());
    }

    @Test void multiplePresetsCoexist() {
        ConfigPreset low = ConfigPreset.builder("low", "Low")
                .set("general", "volume", 10).build();
        ConfigPreset high = ConfigPreset.builder("high", "High")
                .set("general", "volume", 100).build();
        ConfigDefinition definition = baseBuilder().preset(low).preset(high).build();

        assertEquals(2, definition.presets().size());

        definition.applyPreset("low");
        assertEquals(10, ((IntegerOption) definition.option("general", "volume").orElseThrow()).draftValue());

        definition.applyPreset("high");
        assertEquals(100, ((IntegerOption) definition.option("general", "volume").orElseThrow()).draftValue());
    }

    @Test void applyPresetPreservesOtherUnsetOptions() {
        ConfigPreset preset = ConfigPreset.builder("only_volume", "Only Volume")
                .set("general", "volume", 42).build();
        ConfigDefinition definition = baseBuilder().preset(preset).build();

        BooleanOption enabled = (BooleanOption) definition.option("general", "enabled").orElseThrow();
        StringOption name = (StringOption) definition.option("general", "name").orElseThrow();

        // Modify an option not covered by the preset so we can verify it is untouched.
        enabled.setDraftValue(false);
        definition.applyPreset("only_volume");

        assertEquals(42, ((IntegerOption) definition.option("general", "volume").orElseThrow()).draftValue());
        assertEquals(false, enabled.draftValue());
        assertEquals("Player", name.draftValue());
    }

    @Test void presetBuilderKeyUsesTranslatableText() {
        ConfigPreset preset = ConfigPreset.builderKey("my_preset", "mymod.preset.my_preset")
                .set("general", "enabled", false).build();
        assertTrue(preset.displayText().isTranslationKey());
        assertEquals("mymod.preset.my_preset", preset.displayText().value());
    }

    @Test void presetEntryRecordIsImmutable() {
        ConfigPreset.Entry entry = new ConfigPreset.Entry("general", "enabled", false);
        assertEquals("general", entry.categoryId());
        assertEquals("enabled", entry.optionId());
        assertEquals(false, entry.value());
    }

    @Test void definitionWithoutPresetsHasEmptyList() {
        ConfigDefinition definition = baseBuilder().build();
        assertTrue(definition.presets().isEmpty());
        assertTrue(definition.preset("anything").isEmpty());
    }

    @Test void applyPresetRejectsIncompatibleEnumValueAtApply() {
        ConfigPreset preset = ConfigPreset.builder("bad_enum", "Bad Enum")
                .set("general", "mode", 42).build();
        // Passing a non-enum value to an enum option fails at apply time. The exact exception
        // (ClassCastException wrapped or validation failure) depends on the option internals,
        // but in all cases it surfaces as an IllegalArgumentException to the caller.
        ConfigDefinition definition = baseBuilder().preset(preset).build();
        assertThrows(IllegalArgumentException.class, () -> definition.applyPreset("bad_enum"));
    }
}
