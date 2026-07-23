package com.yoima.moddeck.api;

import com.yoima.moddeck.api.option.*;
import com.yoima.moddeck.api.validation.ValidationResult;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
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
        assertEquals("Example description", definition.descriptionText().value());
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

    @Test void floatStepKeepsHumanReadableDecimalValue() {
        FloatOption option = new FloatOption("scale", ConfigText.literal("Scale"), ConfigText.empty(),
                1.0f, 0.5f, 2.0f, 0.1f);
        option.setValue(1.1000001f);
        assertEquals("1.1", Float.toString(option.value()));
    }

    @Test void keybindCanAllowKeyboardMouseAndUnboundInputs() {
        KeybindOption option = new KeybindOption("action", ConfigText.literal("Action"), ConfigText.empty(),
                "key.keyboard.g", java.util.Set.of(
                        KeybindOption.InputType.KEYBOARD, KeybindOption.InputType.MOUSE), true);

        assertTrue(option.trySetValue("key.mouse.left"));
        assertTrue(option.trySetValue(KeybindOption.UNBOUND_KEY));
        assertTrue(option.isUnbound());

        KeybindOption keyboardOnly = new KeybindOption("keyboard", ConfigText.literal("Keyboard"),
                ConfigText.empty(), "key.keyboard.k", java.util.Set.of(KeybindOption.InputType.KEYBOARD), false);
        assertFalse(keyboardOnly.trySetValue("key.mouse.left"));
        assertFalse(keyboardOnly.trySetValue(KeybindOption.UNBOUND_KEY));
        assertEquals("key.keyboard.k", keyboardOnly.value());
    }

    @Test void preservesTranslationKeysAndExplicitCategoryOrder() {
        ConfigDefinition definition = ConfigDefinition.builder("translated_mod")
                .titleKey("translated_mod.config.title")
                .category("late", ConfigText.translatable("translated_mod.category.late"), 20)
                .booleanOption("enabled", ConfigText.translatable("translated_mod.option.enabled"),
                        ConfigText.translatable("translated_mod.option.enabled.desc"), true)
                .category("early", ConfigText.translatable("translated_mod.category.early"), -10)
                .stringOption("name", ConfigText.translatable("translated_mod.option.name"),
                        ConfigText.empty(), "Player", 32)
                .build();

        assertTrue(definition.titleText().isTranslationKey());
        assertEquals("translated_mod.config.title", definition.titleText().value());
        assertEquals(List.of("early", "late"), definition.categories().stream().map(ConfigCategory::id).toList());
        assertEquals(ConfigRoute.parse("moddeck:config/translated_mod"), definition.route());
    }

    @Test void validatesChangesAndInvokesCallbacksAtTheCorrectLifecyclePoint() {
        AtomicInteger changes = new AtomicInteger();
        AtomicInteger optionSaves = new AtomicInteger();
        AtomicInteger screenSaves = new AtomicInteger();
        IntegerOption count = new IntegerOption("count", ConfigText.literal("Count"), ConfigText.empty(),
                2, 0, 10, 1);
        count.validateWith(value -> value % 2 == 0 ? ValidationResult.success()
                        : ValidationResult.invalid(ConfigText.literal("Even values only")))
                .onChanged(value -> changes.incrementAndGet())
                .onSaved(value -> optionSaves.incrementAndGet());
        ConfigDefinition definition = ConfigDefinition.builder("callback_mod")
                .category("general", "General").addOption(count)
                .onSave(screenSaves::incrementAndGet).build();

        assertFalse(count.trySetValue(3));
        assertEquals(2, count.value());
        assertEquals("Even values only", count.validationError().orElseThrow().value());
        assertTrue(count.trySetValue(4));
        assertTrue(definition.isDirty());
        assertEquals(1, changes.get());
        assertEquals(0, optionSaves.get());
        definition.notifySaved();
        assertFalse(definition.isDirty());
        assertEquals(1, optionSaves.get());
        assertEquals(1, screenSaves.get());
    }

    @Test void resetIsDirtyUntilDefaultsAreSaved() {
        BooleanOption enabled = new BooleanOption("enabled", ConfigText.literal("Enabled"),
                ConfigText.empty(), true);
        ConfigDefinition definition = ConfigDefinition.builder("dirty_mod")
                .category("general", "General").addOption(enabled).build();
        enabled.setValue(false);
        definition.notifySaved();
        assertFalse(definition.isDirty());

        definition.reset();
        assertTrue(definition.isDirty());
        definition.notifySaved();
        assertFalse(definition.isDirty());
    }

    @Test void findsAndResetsNestedSubcategoryOptions() {
        StringOption child = new StringOption("child", ConfigText.literal("Child"), ConfigText.empty(), "a", 8);
        SubcategoryOption group = SubcategoryOption.builder("group", ConfigText.literal("Group"))
                .add(child).build();
        ConfigDefinition definition = ConfigDefinition.builder("nested_mod")
                .category("general", "General").addOption(group).build();
        child.setValue("b");

        assertSame(child, definition.option("general", "child").orElseThrow());
        definition.reset();
        assertEquals("a", child.value());
    }
}
