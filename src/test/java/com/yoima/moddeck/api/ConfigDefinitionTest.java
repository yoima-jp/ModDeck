package com.yoima.moddeck.api;

import com.yoima.moddeck.api.option.*;
import com.yoima.moddeck.api.validation.ValidationResult;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
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
                .enumOption("mode", "Mode", Mode.SIMPLE, Mode.class)
                .category("feedback", "Feedback")
                .buttonOption("verify", "Verify", () -> {})
                .build();
        assertEquals(5, definition.categories().getFirst().options().size());
        assertEquals(1, definition.categories().get(1).options().size());
        assertEquals("Example description", definition.descriptionText().value());
        IntegerOption volume = (IntegerOption) definition.option("general", "volume").orElseThrow();
        volume.setDraftValue(80);
        definition.reset();
        assertEquals(50, volume.draftValue());
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

        assertTrue(option.trySetDraftValue("key.mouse.left"));
        assertTrue(option.trySetDraftValue(KeybindOption.UNBOUND_KEY));
        assertTrue(option.isUnbound());

        KeybindOption keyboardOnly = new KeybindOption("keyboard", ConfigText.literal("Keyboard"),
                ConfigText.empty(), "key.keyboard.k", java.util.Set.of(KeybindOption.InputType.KEYBOARD), false);
        assertFalse(keyboardOnly.trySetDraftValue("key.mouse.left"));
        assertFalse(keyboardOnly.trySetDraftValue(KeybindOption.UNBOUND_KEY));
        assertEquals("key.keyboard.k", keyboardOnly.value());
    }

    @Test void keybindCanPersistOrderedModifierChords() {
        KeybindOption option = new KeybindOption("action", ConfigText.literal("Action"), ConfigText.empty(),
                "key.keyboard.g", java.util.Set.of(KeybindOption.InputType.KEYBOARD,
                KeybindOption.InputType.MOUSE), true).allowModifiers(true);
        assertTrue(option.trySetDraftValue("control+shift+key.keyboard.g"));
        assertEquals("key.keyboard.g", KeybindOption.baseKey(option.draftValue()));
        assertTrue(option.trySetDraftValue("alt+key.mouse.left"));
    }

    @Test void requirementsDynamicDefaultsFormattersAndDescriptionAreComposable() {
        BooleanOption enabled = new BooleanOption("enabled", ConfigText.literal("Enabled"), ConfigText.empty(), true);
        AtomicInteger dynamicDefault = new AtomicInteger(4);
        IntegerOption count = new IntegerOption("count", ConfigText.literal("Count"), ConfigText.empty(),
                2, 0, 10, 1);
        count.enabledWhen(ConfigRequirement.isTrue(enabled))
                .displayedWhen(ConfigRequirement.not(ConfigRequirement.isFalse(enabled)))
                .defaultValueFrom(dynamicDefault::get)
                .formatWith(value -> ConfigText.literal(value + " px"));
        assertTrue(count.isEnabled());
        assertEquals("2 px", count.formattedDraftValue().value());
        count.reset();
        assertEquals(4, count.draftValue());
        dynamicDefault.set(6);
        count.reset();
        assertEquals(6, count.draftValue());
        enabled.setDraftValue(false);
        assertFalse(count.isEnabled());
        assertFalse(count.isDisplayed());
        DescriptionOption description = new DescriptionOption("hint", ConfigText.literal("Hint"));
        assertFalse(description.persistent());
        assertFalse(description.isDirty());
    }

    @Test void listSupportsElementValidationAndCreationPolicy() {
        ValueCodec<String> codec = new ValueCodec<>() {
            @Override public String encode(String value) { return value; }
            @Override public String decode(String value) { return value; }
        };
        ListOption<String> option = new ListOption<>("names", ConfigText.literal("Names"), ConfigText.empty(),
                List.of("Alex"), codec, 1, 3).newElementFrom(() -> "Player")
                .validateElementsWith(value -> value.isBlank()
                        ? ValidationResult.invalid(ConfigText.literal("Required")) : ValidationResult.success());
        assertEquals("Player", option.newElement());
        assertEquals("Player", option.newElementText());
        assertFalse(option.trySetDraftValue(List.of("")));
        assertTrue(option.trySetDraftValue(List.of("Alex", "Steve")));
    }

    @Test void listWithoutNewElementSupplierStartsWithBlankEditableText() {
        ValueCodec<String> codec = new ValueCodec<>() {
            @Override public String encode(String value) { return value; }
            @Override public String decode(String value) { return value; }
        };
        ListOption<String> option = new ListOption<>("names", ConfigText.literal("Names"), ConfigText.empty(),
                List.of(), codec, 0, 3);

        assertEquals("", option.newElementText());
        assertEquals("", option.newElement());

        ValueCodec<Integer> integerCodec = new ValueCodec<>() {
            @Override public String encode(Integer value) { return value.toString(); }
            @Override public Integer decode(String value) { return value.isBlank() ? 0 : Integer.parseInt(value); }
        };
        ListOption<Integer> numbers = new ListOption<>("numbers", ConfigText.literal("Numbers"),
                ConfigText.empty(), List.of(1), integerCodec, 0, 3);
        assertEquals("", numbers.newElementText());
        assertEquals(0, numbers.newElement());
    }

    @Test void listAcceptsBlankNewElementForStringAndNumericCodecs() {
        ValueCodec<String> stringCodec = new ValueCodec<>() {
            @Override public String encode(String value) { return value; }
            @Override public String decode(String value) { return value; }
        };
        ListOption<String> strings = new ListOption<>("tags", ConfigText.literal("Tags"), ConfigText.empty(),
                List.of("a"), stringCodec, 0, 3)
                .validateElementsWith(value -> value.isBlank()
                        ? ValidationResult.invalid(ConfigText.literal("Required")) : ValidationResult.success());
        // newElementText is blank when no supplier is configured, matching the in-game empty row.
        assertEquals("", strings.newElementText());
        assertEquals("", strings.newElement());
        assertTrue(strings.trySetDraftValue(List.of("a", "b")));

        ValueCodec<Integer> integerCodec = new ValueCodec<>() {
            @Override public String encode(Integer value) { return value.toString(); }
            @Override public Integer decode(String value) { return value.isBlank() ? 0 : Integer.parseInt(value); }
        };
        ListOption<Integer> numbers = new ListOption<>("scores", ConfigText.literal("Scores"), ConfigText.empty(),
                List.of(10), integerCodec, 0, 3)
                .validateElementsWith(value -> value < 0 || value > 100
                        ? ValidationResult.invalid(ConfigText.literal("Range")) : ValidationResult.success());
        assertEquals("", numbers.newElementText());
        assertEquals(Integer.valueOf(0), numbers.newElement());
        assertTrue(numbers.trySetDraftValue(List.of(10, 0)));
        assertEquals(List.of(10, 0), numbers.draftValue());
    }

    @Test void keybindCanBeKeyboardOnlyWithoutUnboundOrMouse() {
        KeybindOption option = new KeybindOption("keyboard_only", ConfigText.literal("Keyboard only"),
                ConfigText.empty(), "key.keyboard.k", Set.of(KeybindOption.InputType.KEYBOARD), false);
        assertTrue(option.trySetDraftValue("key.keyboard.l"));
        assertFalse(option.trySetDraftValue("key.mouse.left"));
        assertFalse(option.trySetDraftValue(KeybindOption.UNBOUND_KEY));
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

    @Test void buttonOptionRunsItsActionWithoutBecomingPersistentOrDirty() {
        AtomicInteger actions = new AtomicInteger();
        ConfigDefinition definition = ConfigDefinition.builder("button_mod")
                .category("general", "General")
                .buttonOption("reload", ConfigText.literal("Reload"), ConfigText.literal("Reload data"),
                        ConfigText.literal("Run"), actions::incrementAndGet)
                .build();

        ButtonOption button = (ButtonOption) definition.option("general", "reload").orElseThrow();
        assertEquals("Run", button.buttonText().value());
        assertTrue(button.runAction());
        assertEquals(1, actions.get());
        assertFalse(button.persistent());
        assertFalse(definition.isDirty());
    }

    @Test void buttonOptionContainsCallbackFailures() {
        ButtonOption button = new ButtonOption("failure", ConfigText.literal("Failure"), ConfigText.empty(),
                ConfigText.literal("Run"), () -> { throw new IllegalStateException("boom"); });

        assertFalse(button.runAction());
        assertFalse(button.isDirty());
    }

    @Test void buttonOptionDoesNotMakeDefinitionDirtyOrPersistent() {
        AtomicInteger actions = new AtomicInteger();
        ConfigDefinition definition = ConfigDefinition.builder("feedback_mod")
                .category("feedback", "Feedback")
                .buttonOption("verify", ConfigText.literal("Verify"), ConfigText.literal("Log feedback"),
                        ConfigText.literal("Check"), () -> {
                            actions.incrementAndGet();
                            throw new IllegalStateException("expected test failure");
                        })
                .build();

        ButtonOption button = (ButtonOption) definition.option("feedback", "verify").orElseThrow();
        assertFalse(button.persistent());
        assertFalse(button.isDirty());
        assertFalse(button.runAction());
        assertEquals(1, actions.get());
        assertFalse(definition.isDirty());
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

        assertFalse(count.trySetDraftValue(3));
        assertEquals(2, count.value());
        assertEquals("Even values only", count.validationError().orElseThrow().value());
        assertTrue(count.trySetDraftValue(4));
        assertTrue(definition.isDirty());
        assertEquals(2, count.value());
        assertEquals(4, count.draftValue());
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

    @Test void discardRestoresLastAppliedValueWithoutSaveCallbacks() {
        AtomicInteger saves = new AtomicInteger();
        StringOption name = new StringOption("name", ConfigText.literal("Name"), ConfigText.empty(), "saved", 32);
        name.onSaved(value -> saves.incrementAndGet());
        ConfigDefinition definition = ConfigDefinition.builder("discard_mod")
                .category("general", "General").addOption(name).build();

        name.setDraftValue("draft");
        assertEquals("saved", name.value());
        assertEquals("draft", name.draftValue());
        definition.discardChanges();

        assertEquals("saved", name.draftValue());
        assertFalse(definition.isDirty());
        assertEquals(0, saves.get());
    }

    @Test void findsAndResetsNestedSubcategoryOptions() {
        StringOption child = new StringOption("child", ConfigText.literal("Child"), ConfigText.empty(), "a", 8);
        SubcategoryOption group = SubcategoryOption.builder("group", ConfigText.literal("Group"))
                .add(child).build();
        ConfigDefinition definition = ConfigDefinition.builder("nested_mod")
                .category("general", "General").addOption(group).build();
        child.setDraftValue("b");

        assertSame(child, definition.option("general", "child").orElseThrow());
        definition.reset();
        assertEquals("a", child.draftValue());
    }
}
