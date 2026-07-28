---
title: Complete Example: Full Config with All Option Types
summary: A self-contained, compilable example exercising every built-in option type, metadata API, and preset.
audience:
  - mod-developer
  - coding-agent
environment:
  - common
related_packages:
  - com.yoima.moddeck.api
  - com.yoima.moddeck.api.option
  - com.yoima.moddeck.api.validation
last_verified_commit: "4790b77de8b5c728d89099e1ac7637c4b99f1b29"
---

# Complete Example: Full Config with All Option Types

This example is adapted from the actual `ExampleConfig.java` in the repository's `internal.example` package. It exercises every built-in option type, metadata APIs, conditional entries, subcategories, and presets.

```java
package com.example.mymod;

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

public final class FullConfigExample {
    private static final Logger LOGGER = Logger.getLogger(FullConfigExample.class.getName());

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

    public enum DisplayMode { SIMPLE, DETAILED, COMPACT }

    public static void register() {
        // --- Boolean with dynamic tooltip and search aliases ---
        BooleanOption enabled = new BooleanOption(
            "enabled",
            ConfigText.translatable("mymod.option.enabled"),
            ConfigText.translatable("mymod.option.enabled.desc"),
            true
        );
        enabled.tooltip(value -> List.of(
                ConfigText.translatable(value ? "mymod.tooltip.enabled" : "mymod.tooltip.disabled")
            ))
            .addSearchAliases("master", "toggle");

        // --- String with validation and read-only field ---
        StringOption profile = new StringOption(
            "profile",
            ConfigText.translatable("mymod.option.profile"),
            ConfigText.translatable("mymod.option.profile.desc"),
            "Player", 32
        );
        profile.validateWith(value -> value.isBlank()
            ? ValidationResult.invalid(ConfigText.translatable("mymod.validation.profile"))
            : ValidationResult.success()
        );

        StringOption generatedPath = new StringOption(
            "generated_path",
            ConfigText.translatable("mymod.option.generated_path"),
            ConfigText.translatable("mymod.option.generated_path.desc"),
            "config/mymod.json", 128
        );
        generatedPath.editable(false);

        // --- Numeric types with formatters and dynamic defaults ---
        IntegerOption volume = new IntegerOption(
            "volume",
            ConfigText.translatable("mymod.option.volume"),
            ConfigText.translatable("mymod.option.volume.desc"),
            75, 0, 100, 1
        );
        volume.formatWith(value -> ConfigText.literal(value + "%"));

        LongOption cache = new LongOption(
            "cache_limit",
            ConfigText.translatable("mymod.option.cache"),
            ConfigText.translatable("mymod.option.cache.desc"),
            4096L, 0L, 65536L, 256L
        );
        cache.formatWith(value -> ConfigText.literal(value + " MiB"))
             .requiresRestart();

        FloatOption scale = new FloatOption(
            "scale",
            ConfigText.translatable("mymod.option.scale"),
            ConfigText.translatable("mymod.option.scale.desc"),
            1.0f, 0.5f, 2.0f, 0.1f
        );
        scale.formatWith(value -> ConfigText.literal(String.format(Locale.ROOT, "%.1fx", value)));

        DoubleOption opacity = new DoubleOption(
            "opacity",
            ConfigText.translatable("mymod.option.opacity"),
            ConfigText.translatable("mymod.option.opacity.desc"),
            0.65, 0.0, 1.0, 0.05
        );
        opacity.formatWith(value -> ConfigText.literal(Math.round(value * 100) + "%"))
               .defaultValueFrom(() -> 0.75);

        // --- Custom validator: even numbers only ---
        IntegerOption evenNumber = new IntegerOption(
            "even_number",
            ConfigText.translatable("mymod.option.even"),
            ConfigText.translatable("mymod.option.even.desc"),
            4, 0, 20, 1
        );
        evenNumber.validateWith(value -> value % 2 == 0
            ? ValidationResult.success()
            : ValidationResult.invalid(ConfigText.translatable("mymod.validation.even"))
        );

        // --- Enum with custom labels ---
        EnumOption<DisplayMode> mode = new EnumOption<>(
            "display_mode",
            ConfigText.translatable("mymod.option.mode"),
            ConfigText.translatable("mymod.option.mode.desc"),
            DisplayMode.DETAILED, DisplayMode.class
        ).labels(value -> ConfigText.translatable("mymod.mode." + value.name().toLowerCase(Locale.ROOT)));

        // --- Selector with conditional enable ---
        SelectorOption<String> quality = new SelectorOption<>(
            "quality",
            ConfigText.translatable("mymod.option.quality"),
            ConfigText.translatable("mymod.option.quality.desc"),
            "balanced", List.of("fast", "balanced", "quality"), STRING_CODEC,
            value -> ConfigText.translatable("mymod.quality." + value)
        );
        quality.enabledWhen(ConfigRequirement.isTrue(enabled));

        // --- Keybind with modifiers and mouse ---
        KeybindOption actionKey = new KeybindOption(
            "action_key",
            ConfigText.translatable("mymod.option.key"),
            ConfigText.translatable("mymod.option.key.desc"),
            "key.keyboard.g",
            Set.of(KeybindOption.InputType.KEYBOARD, KeybindOption.InputType.MOUSE),
            true
        ).allowModifiers(true);

        // --- Keybind keyboard-only, no unbound ---
        KeybindOption menuKey = new KeybindOption(
            "menu_key",
            ConfigText.translatable("mymod.option.menu_key"),
            ConfigText.translatable("mymod.option.menu_key.desc"),
            "key.keyboard.m",
            Set.of(KeybindOption.InputType.KEYBOARD), false
        );

        // --- List with element validation and supplier ---
        ListOption<String> tags = new ListOption<>(
            "tags",
            ConfigText.translatable("mymod.option.tags"),
            ConfigText.translatable("mymod.option.tags.desc"),
            List.of("example", "deck"), STRING_CODEC, 0, 16
        ).newElementFrom(() -> "")
         .validateElementsWith(value -> value.isBlank()
            ? ValidationResult.invalid(ConfigText.translatable("mymod.validation.list_blank"))
            : ValidationResult.success()
        );

        // --- List with numeric elements ---
        ListOption<Integer> thresholds = new ListOption<>(
            "thresholds",
            ConfigText.translatable("mymod.option.thresholds"),
            ConfigText.translatable("mymod.option.thresholds.desc"),
            List.of(25, 50, 75), INTEGER_CODEC, 1, 8
        ).newElementFrom(() -> 100)
         .validateElementsWith(value -> value >= 0 && value <= 100
            ? ValidationResult.success()
            : ValidationResult.invalid(ConfigText.translatable("mymod.validation.threshold"))
        );

        // --- Fixed-size list (no insert/delete/reorder) ---
        ListOption<String> fixedOrder = new ListOption<>(
            "fixed_order",
            ConfigText.translatable("mymod.option.fixed_order"),
            ConfigText.translatable("mymod.option.fixed_order.desc"),
            List.of("first", "second"), STRING_CODEC, 2, 2
        ).newElementFrom(() -> "item")
         .allowInsertion(false)
         .allowDeletion(false)
         .allowReordering(false);

        // --- Colors ---
        ColorOption accent = new ColorOption(
            "accent",
            ConfigText.translatable("mymod.option.accent"),
            ConfigText.translatable("mymod.option.accent.desc"),
            0x8B5CF6, false
        );

        ColorOption overlay = new ColorOption(
            "overlay",
            ConfigText.translatable("mymod.option.overlay"),
            ConfigText.translatable("mymod.option.overlay.desc"),
            0xCC221144, true
        );

        ColorOption conditionalColor = new ColorOption(
            "conditional_color",
            ConfigText.translatable("mymod.option.conditional_color"),
            ConfigText.translatable("mymod.option.conditional_color.desc"),
            0x55D6BE, false
        );
        conditionalColor.enabledWhen(ConfigRequirement.isTrue(enabled));

        // --- Subcategory with conditional display ---
        BooleanOption diagnostics = new BooleanOption(
            "diagnostics",
            ConfigText.translatable("mymod.option.diagnostics"),
            ConfigText.translatable("mymod.option.diagnostics.desc"),
            false
        );
        diagnostics.requiresRestart();

        StringOption diagnosticLabel = new StringOption(
            "diagnostic_label",
            ConfigText.translatable("mymod.option.diagnostic_label"),
            ConfigText.translatable("mymod.option.diagnostic_label.desc"),
            "Example trace", 64
        );
        diagnosticLabel.displayedWhen(ConfigRequirement.isTrue(diagnostics));

        SubcategoryOption advanced = SubcategoryOption.builder(
            "advanced_group",
            ConfigText.translatable("mymod.subcategory.advanced")
        )
        .description(ConfigText.translatable("mymod.subcategory.advanced.desc"))
        .initiallyExpanded(false)
        .add(diagnostics)
        .add(diagnosticLabel)
        .build();

        // --- Button ---
        ButtonOption verifyButton = new ButtonOption(
            "verify_button",
            ConfigText.translatable("mymod.option.verify_button"),
            ConfigText.translatable("mymod.option.verify_button.desc"),
            ConfigText.translatable("mymod.option.verify_button.label"),
            () -> LOGGER.info("Example Mod button action completed")
        );

        // --- Presets ---
        ConfigPreset performancePreset = ConfigPreset.builderKey(
                "performance", "mymod.preset.performance")
            .set("numbers", "cache_limit", 2048L)
            .set("choices", "display_mode", DisplayMode.COMPACT)
            .set("choices", "quality", "fast")
            .build();

        ConfigPreset qualityPreset = ConfigPreset.builderKey(
                "quality", "mymod.preset.quality")
            .set("numbers", "cache_limit", 8192L)
            .set("choices", "display_mode", DisplayMode.DETAILED)
            .set("choices", "quality", "quality")
            .build();

        // --- Register ---
        ConfigScreenApi.register(
            ConfigDefinition.builder("mymod")
                .titleKey("mymod.config.title")
                .descriptionKey("mymod.config.description")
                .categoryKey("general", "mymod.category.general")
                .descriptionEntry("general_hint", ConfigText.translatable("mymod.description.general"))
                .addOption(enabled)
                .addOption(profile)
                .addOption(generatedPath)
                .categoryKey("numbers", "mymod.category.numbers")
                .descriptionEntry("numbers_hint", ConfigText.translatable("mymod.description.numbers"))
                .addOption(volume)
                .addOption(cache)
                .addOption(scale)
                .addOption(opacity)
                .addOption(evenNumber)
                .categoryKey("choices", "mymod.category.choices")
                .addOption(mode)
                .addOption(quality)
                .addOption(actionKey)
                .addOption(menuKey)
                .categoryKey("lists", "mymod.category.lists")
                .descriptionEntry("lists_hint", ConfigText.translatable("mymod.description.lists"))
                .addOption(tags)
                .addOption(thresholds)
                .addOption(fixedOrder)
                .categoryKey("appearance", "mymod.category.appearance")
                .addOption(accent)
                .addOption(overlay)
                .addOption(conditionalColor)
                .categoryKey("feedback", "mymod.category.feedback")
                .descriptionEntry("feedback_hint", ConfigText.translatable("mymod.description.feedback"))
                .addOption(verifyButton)
                .categoryKey("advanced", "mymod.category.advanced")
                .descriptionEntry("advanced_hint", ConfigText.translatable("mymod.description.advanced"))
                .addOption(advanced)
                .preset(performancePreset)
                .preset(qualityPreset)
                .build()
        );
    }
}
```

## Notes on this example

- Every option is constructed directly (not via builder convenience methods) so that metadata methods can be chained.
- `enabled` is used as a condition for `quality` and `conditionalColor`.
- `diagnostics` controls the display of `diagnosticLabel` inside a subcategory.
- `opacity` has a dynamic default (`0.75`) that differs from the construction default (`0.65`).
- `evenNumber` has a custom validator that rejects odd values.
- `fixedOrder` is a fixed-size list with all modifications disabled.
- `actionKey` allows keyboard, mouse, modifiers, and unbound.
- `menuKey` is keyboard-only, no unbound, no mouse.
- `performancePreset` and `qualityPreset` target options across multiple categories.
- `verifyButton` is a non-persistent action button.
- `advanced` is a subcategory that is collapsed by default.