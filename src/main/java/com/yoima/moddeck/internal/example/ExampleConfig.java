package com.yoima.moddeck.internal.example;

import com.yoima.moddeck.api.ConfigDefinition;
import com.yoima.moddeck.api.ConfigPreset;
import com.yoima.moddeck.api.ConfigRequirement;
import com.yoima.moddeck.api.ConfigScreenApi;
import com.yoima.moddeck.api.ConfigText;
import com.yoima.moddeck.api.option.*;
import com.yoima.moddeck.api.validation.ValidationResult;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

/** Development catalog exercising every built-in entry and the reusable option metadata APIs. */
public final class ExampleConfig {
    private static final Logger LOGGER = Logger.getLogger(ExampleConfig.class.getName());
    private static final ValueCodec<String> STRING_CODEC = new ValueCodec<>() {
        @Override public String encode(String value) { return value; }
        @Override public String decode(String value) { return value; }
    };
    private static final ValueCodec<Integer> INTEGER_CODEC = new ValueCodec<>() {
        @Override public String encode(Integer value) { return value.toString(); }
        @Override public Integer decode(String value) {
            if (value == null || value.isBlank()) return 0;
            return Integer.parseInt(value);
        }
    };

    private ExampleConfig() {}

    public enum DisplayMode { SIMPLE, DETAILED, COMPACT }

    public static void register() {
        BooleanOption enabled = new BooleanOption("enabled", key("option.enabled"), key("option.enabled.desc"), true);
        enabled.tooltip(value -> List.of(key(value ? "tooltip.enabled" : "tooltip.disabled")))
                .addSearchAliases("master", "toggle");

        StringOption profile = new StringOption("profile", key("option.profile"), key("option.profile.desc"),
                "Player", 32);
        profile.validateWith(value -> value.isBlank()
                ? ValidationResult.invalid(key("validation.profile")) : ValidationResult.success());
        StringOption generatedPath = new StringOption("generated_path", key("option.generated_path"),
                key("option.generated_path.desc"), "config/example_mod.json", 128);
        generatedPath.editable(false);

        IntegerOption volume = new IntegerOption("volume", key("option.volume"), key("option.volume.desc"),
                75, 0, 100, 1);
        volume.formatWith(value -> ConfigText.literal(value + "%"));
        LongOption cache = new LongOption("cache_limit", key("option.cache"), key("option.cache.desc"),
                4096L, 0L, 65536L, 256L);
        cache.formatWith(value -> ConfigText.literal(value + " MiB")).requiresRestart();
        FloatOption scale = new FloatOption("scale", key("option.scale"), key("option.scale.desc"),
                1.0f, 0.5f, 2.0f, 0.1f);
        scale.formatWith(value -> ConfigText.literal(String.format(Locale.ROOT, "%.1fx", value)));
        DoubleOption opacity = new DoubleOption("opacity", key("option.opacity"), key("option.opacity.desc"),
                0.65, 0, 1, 0.05);
        opacity.formatWith(value -> ConfigText.literal(Math.round(value * 100) + "%"))
                .defaultValueFrom(() -> 0.75);
        IntegerOption evenNumber = new IntegerOption("even_number", key("option.even"), key("option.even.desc"),
                4, 0, 20, 1);
        evenNumber.validateWith(value -> value % 2 == 0
                ? ValidationResult.success() : ValidationResult.invalid(key("validation.even")));

        EnumOption<DisplayMode> mode = new EnumOption<>("display_mode", key("option.mode"), key("option.mode.desc"),
                DisplayMode.DETAILED, DisplayMode.class).labels(value -> key("mode." + value.name().toLowerCase(Locale.ROOT)));
        SelectorOption<String> quality = new SelectorOption<>("quality", key("option.quality"),
                key("option.quality.desc"), "balanced", List.of("fast", "balanced", "quality"), STRING_CODEC,
                value -> key("quality." + value));
        quality.enabledWhen(ConfigRequirement.isTrue(enabled));
        KeybindOption actionKey = new KeybindOption("action_key", key("option.key"), key("option.key.desc"),
                "key.keyboard.g", Set.of(KeybindOption.InputType.KEYBOARD, KeybindOption.InputType.MOUSE), true)
                .allowModifiers(true);
        KeybindOption menuKey = new KeybindOption("menu_key", key("option.menu_key"), key("option.menu_key.desc"),
                "key.keyboard.m", Set.of(KeybindOption.InputType.KEYBOARD), false);

        ListOption<String> tags = new ListOption<>("tags", key("option.tags"), key("option.tags.desc"),
                List.of("example", "deck"), STRING_CODEC, 0, 16).newElementFrom(() -> "");
        tags.validateElementsWith(value -> value.isBlank()
                ? ValidationResult.invalid(key("validation.list_blank")) : ValidationResult.success());
        ListOption<Integer> thresholds = new ListOption<>("thresholds", key("option.thresholds"),
                key("option.thresholds.desc"), List.of(25, 50, 75), INTEGER_CODEC, 1, 8)
                .newElementFrom(() -> 100)
                .validateElementsWith(value -> value >= 0 && value <= 100
                        ? ValidationResult.success() : ValidationResult.invalid(key("validation.threshold")));
        ListOption<String> fixedOrder = new ListOption<>("fixed_order", key("option.fixed_order"),
                key("option.fixed_order.desc"), List.of("first", "second"), STRING_CODEC, 2, 2)
                .newElementFrom(() -> "item").allowInsertion(false).allowDeletion(false).allowReordering(false);

        ColorOption accent = new ColorOption("accent", key("option.accent"), key("option.accent.desc"),
                0x8B5CF6, false);
        ColorOption overlay = new ColorOption("overlay", key("option.overlay"), key("option.overlay.desc"),
                0xCC221144, true);
        ColorOption conditionalColor = new ColorOption("conditional_color", key("option.conditional_color"),
                key("option.conditional_color.desc"), 0x55D6BE, false);
        conditionalColor.enabledWhen(ConfigRequirement.isTrue(enabled));

        BooleanOption diagnostics = new BooleanOption("diagnostics", key("option.diagnostics"),
                key("option.diagnostics.desc"), false);
        diagnostics.requiresRestart();
        StringOption diagnosticLabel = new StringOption("diagnostic_label", key("option.diagnostic_label"),
                key("option.diagnostic_label.desc"), "Example trace", 64);
        diagnosticLabel.displayedWhen(ConfigRequirement.isTrue(diagnostics));
        SubcategoryOption advanced = SubcategoryOption.builder("advanced_group", key("subcategory.advanced"))
                .description(key("subcategory.advanced.desc"))
                .initiallyExpanded(false)
                .add(diagnostics)
                .add(diagnosticLabel)
                .build();

        // Intentionally no newElementFrom: verifies blank-row insertion for editable string lists.
        ListOption<String> blankTags = new ListOption<>("blank_tags", key("option.blank_tags"),
                key("option.blank_tags.desc"), List.of("sample"), STRING_CODEC, 0, 8)
                .validateElementsWith(value -> value.isBlank()
                        ? ValidationResult.invalid(key("validation.blank_tags")) : ValidationResult.success());
        // Intentionally no newElementFrom: verifies blank-row insertion for editable numeric lists.
        ListOption<Integer> blankScores = new ListOption<>("blank_scores", key("option.blank_scores"),
                key("option.blank_scores.desc"), List.of(10), INTEGER_CODEC, 0, 8)
                .validateElementsWith(value -> value < 0 || value > 100
                        ? ValidationResult.invalid(key("validation.blank_scores")) : ValidationResult.success());
        // Keyboard-only keybind that cannot be unbound or assigned to mouse buttons.
        KeybindOption keyboardOnlyKey = new KeybindOption("keyboard_only_key", key("option.keyboard_only_key"),
                key("option.keyboard_only_key.desc"), "key.keyboard.n",
                Set.of(KeybindOption.InputType.KEYBOARD), false);
        // Keep demonstration feedback in the log so the settings UI does not gain test-only state.
        ButtonOption verifyButton = new ButtonOption("verify_button", key("option.verify_button"),
                key("option.verify_button.desc"), key("option.verify_button.label"),
                () -> LOGGER.info("Example Mod button action completed"));
        ConfigPreset performancePreset = ConfigPreset.builderKey(
                        "performance", "example_mod.preset.performance")
                .set("numbers", "cache_limit", 2048L)
                .set("choices", "display_mode", DisplayMode.COMPACT)
                .set("choices", "quality", "fast")
                .build();
        ConfigPreset qualityPreset = ConfigPreset.builderKey(
                        "quality", "example_mod.preset.quality")
                .set("numbers", "cache_limit", 8192L)
                .set("choices", "display_mode", DisplayMode.DETAILED)
                .set("choices", "quality", "quality")
                .build();

        ConfigScreenApi.register(ConfigDefinition.builder("example_mod")
                .titleKey("example_mod.config.title")
                .descriptionKey("example_mod.config.description")
                .categoryKey("general", "example_mod.category.general")
                .descriptionEntry("general_hint", key("description.general"))
                .addOption(enabled).addOption(profile).addOption(generatedPath)
                .categoryKey("numbers", "example_mod.category.numbers")
                .descriptionEntry("numbers_hint", key("description.numbers"))
                .addOption(volume).addOption(cache).addOption(scale).addOption(opacity).addOption(evenNumber)
                .categoryKey("choices", "example_mod.category.choices")
                .addOption(mode).addOption(quality).addOption(actionKey).addOption(menuKey)
                .addOption(keyboardOnlyKey)
                .categoryKey("lists", "example_mod.category.lists")
                .descriptionEntry("lists_hint", key("description.lists"))
                .addOption(tags).addOption(thresholds).addOption(fixedOrder)
                .addOption(blankTags).addOption(blankScores)
                .categoryKey("appearance", "example_mod.category.appearance")
                .addOption(accent).addOption(overlay).addOption(conditionalColor)
                .categoryKey("feedback", "example_mod.category.feedback")
                .descriptionEntry("feedback_hint", key("description.feedback"))
                .addOption(verifyButton)
                .categoryKey("advanced", "example_mod.category.advanced")
                .descriptionEntry("advanced_hint", key("description.advanced"))
                .addOption(advanced)
                .preset(performancePreset)
                .preset(qualityPreset)
                .build());
    }

    private static ConfigText key(String suffix) { return ConfigText.translatable("example_mod." + suffix); }
}
