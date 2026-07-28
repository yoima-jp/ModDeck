---
title: Registering Options
summary: Complete guide to every built-in option type and the metadata APIs on ConfigOption.
audience:
  - mod-developer
  - coding-agent
environment:
  - common
related_packages:
  - com.yoima.moddeck.api.option
  - com.yoima.moddeck.api.validation
last_verified_commit: "4790b77de8b5c728d89099e1ac7637c4b99f1b29"
---

# Registering Options

## Goal

Add typed configuration entries to a `ConfigDefinition.Builder` using the built-in option types and configure their metadata (validation, tooltips, formatters, callbacks, conditional display, search aliases, restart-required, read-only state).

## Use this when

- You are building a `ConfigDefinition` with the builder API.
- You need to know which option type to use for a given value and how to configure it.

## Do not use this when

- You want annotation-driven field mapping — see [AutoConfig Guide](auto-config.md).
- You need a completely custom option class — see [Custom Options](custom-options.md).

## Prerequisites

- Read [Getting Started](getting-started.md) first.
- Source set: **common** (`src/main/java`).

## Built-in option types

| Option type | Java type | Presentation | Persistent? | Notes |
| --- | --- | --- | --- | --- |
| `BooleanOption` | `Boolean` | `TOGGLE` | Yes | |
| `IntegerOption` | `Integer` | `SLIDER` | Yes | Range and step required. |
| `LongOption` | `Long` | `SLIDER` | Yes | Range and step required. |
| `FloatOption` | `Float` | `SLIDER` | Yes | Range and step required. |
| `DoubleOption` | `Double` | `SLIDER` | Yes | Range and step required. |
| `StringOption` | `String` | `TEXT_FIELD` | Yes | Maximum length enforced. |
| `EnumOption<E>` | `E extends Enum<E>` | `SELECTOR` | Yes | Cycles through `enumType.getEnumConstants()`. |
| `ColorOption` | `Integer` | `COLOR` | Yes | RGB (6 hex) or ARGB (8 hex). |
| `KeybindOption` | `String` | `KEYBIND` | Yes | Stores Minecraft key identifiers. |
| `ListOption<T>` | `List<T>` | `LIST` | Yes | Generic list with `ValueCodec<T>`. |
| `SelectorOption<T>` | `T` | `DROPDOWN` | Yes | Custom choices with `ValueCodec<T>` and label factory. |
| `SubcategoryOption` | `Boolean` | `SUBCATEGORY` | No (expansion state only) | Children are persistent individually. |
| `DescriptionOption` | `String` | `DESCRIPTION` | No | Non-interactive explanatory row. |
| `ButtonOption` | `Boolean` | `BUTTON` | No | Runs an action callback. |

## Complete example

```java
package com.example.mymod;

import com.yoima.moddeck.api.ConfigDefinition;
import com.yoima.moddeck.api.ConfigRequirement;
import com.yoima.moddeck.api.ConfigScreenApi;
import com.yoima.moddeck.api.ConfigText;
import com.yoima.moddeck.api.option.*;
import com.yoima.moddeck.api.validation.ValidationResult;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MyModConfig {
    public enum DisplayMode { SIMPLE, DETAILED, COMPACT }

    public static void register() {
        BooleanOption enabled = new BooleanOption(
            "enabled",
            ConfigText.translatable("mymod.option.enabled"),
            ConfigText.translatable("mymod.option.enabled.desc"),
            true
        );
        enabled.tooltip(value -> List.of(
                ConfigText.translatable(value ? "mymod.tooltip.on" : "mymod.tooltip.off")
            ))
            .addSearchAliases("master", "toggle");

        IntegerOption volume = new IntegerOption(
            "volume",
            ConfigText.translatable("mymod.option.volume"),
            ConfigText.translatable("mymod.option.volume.desc"),
            75, 0, 100, 1
        );
        volume.formatWith(value -> ConfigText.literal(value + "%"));

        DoubleOption opacity = new DoubleOption(
            "opacity",
            ConfigText.translatable("mymod.option.opacity"),
            ConfigText.translatable("mymod.option.opacity.desc"),
            0.65, 0.0, 1.0, 0.05
        );
        opacity.defaultValueFrom(() -> 0.75)
               .requiresRestart();

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

        EnumOption<DisplayMode> mode = new EnumOption<>(
            "display_mode",
            ConfigText.translatable("mymod.option.mode"),
            ConfigText.translatable("mymod.option.mode.desc"),
            DisplayMode.DETAILED, DisplayMode.class
        ).labels(value -> ConfigText.translatable("mymod.mode." + value.name().toLowerCase(Locale.ROOT)));

        ColorOption accent = new ColorOption(
            "accent",
            ConfigText.translatable("mymod.option.accent"),
            ConfigText.translatable("mymod.option.accent.desc"),
            0x8B5CF6, false
        );

        KeybindOption actionKey = new KeybindOption(
            "action_key",
            ConfigText.translatable("mymod.option.key"),
            ConfigText.translatable("mymod.option.key.desc"),
            "key.keyboard.g",
            Set.of(KeybindOption.InputType.KEYBOARD, KeybindOption.InputType.MOUSE),
            true
        ).allowModifiers(true);

        ValueCodec<String> stringCodec = new ValueCodec<>() {
            @Override public String encode(String value) { return value; }
            @Override public String decode(String value) { return value; }
        };

        ListOption<String> tags = new ListOption<>(
            "tags",
            ConfigText.translatable("mymod.option.tags"),
            ConfigText.translatable("mymod.option.tags.desc"),
            List.of("example"), stringCodec, 0, 16
        ).newElementFrom(() -> "")
         .validateElementsWith(value -> value.isBlank()
            ? ValidationResult.invalid(ConfigText.translatable("mymod.validation.blank"))
            : ValidationResult.success()
        );

        SelectorOption<String> quality = new SelectorOption<>(
            "quality",
            ConfigText.translatable("mymod.option.quality"),
            ConfigText.translatable("mymod.option.quality.desc"),
            "balanced", List.of("fast", "balanced", "quality"), stringCodec,
            value -> ConfigText.translatable("mymod.quality." + value)
        );
        quality.enabledWhen(ConfigRequirement.isTrue(enabled));

        ButtonOption reload = new ButtonOption(
            "reload",
            ConfigText.translatable("mymod.option.reload"),
            ConfigText.translatable("mymod.option.reload.desc"),
            ConfigText.translatable("mymod.option.reload.label"),
            () -> System.out.println("Reload triggered")
        );

        SubcategoryOption advanced = SubcategoryOption.builder(
            "advanced_group",
            ConfigText.translatable("mymod.subcategory.advanced")
        )
        .description(ConfigText.translatable("mymod.subcategory.advanced.desc"))
        .initiallyExpanded(false)
        .add(opacity)
        .add(profile)
        .build();

        ConfigScreenApi.register(
            ConfigDefinition.builder("mymod")
                .titleKey("mymod.config.title")
                .categoryKey("general", "mymod.category.general")
                .addOption(enabled)
                .addOption(mode)
                .addOption(quality)
                .addOption(volume)
                .addOption(accent)
                .addOption(actionKey)
                .addOption(tags)
                .addOption(reload)
                .addOption(advanced)
                .build()
        );
    }
}
```

## How it works

Each option is a self-contained `ConfigOption<T>` instance. The builder's convenience methods (`booleanOption`, `integerOption`, etc.) construct the option internally, but you can also construct options directly and add them with `addOption(ConfigOption<?>)`.

After construction, you chain metadata methods on the option instance. These methods are available on all option types because they are defined on `ConfigOption<T>`:

| Method | Effect |
| --- | --- |
| `validateWith(ConfigValidator<T>)` | Adds a custom validator. Invalid values are rejected on `setDraftValue` / `setValue`. |
| `defaultValueFrom(Supplier<T>)` | Replaces the fixed default with a dynamic supplier. Called on `reset()`. |
| `onChanged(Consumer<T>)` | Invoked when `draftValue` changes through `setDraftValue`. |
| `onSaved(Consumer<T>)` | Invoked when the option's value is committed via `notifySaved()`. |
| `tooltip(ConfigText...)` or `tooltip(Function<T, List<ConfigText>>)` | Sets static or dynamic tooltips. |
| `formatWith(Function<T, ConfigText>)` | Custom display formatting for the draft value. |
| `enabledWhen(ConfigRequirement)` | Disables the widget when the requirement is false. |
| `displayedWhen(ConfigRequirement)` | Hides the row when the requirement is false. |
| `addSearchAliases(String...)` | Adds lowercase search aliases. |
| `requiresRestart()` | Marks the option as requiring a restart after change. |
| `editable(boolean)` | When false, the widget is read-only. |

## Lifecycle and side effects

- `onChanged` fires only when `setDraftValue` produces a different value than the current draft. It does not fire on `setValue` (which sets both value and draft).
- `onSaved` fires during `notifySaved()`, which is called by `ConfigScreenApi.save` after persistence succeeds. It fires once per option per save.
- If a callback throws `RuntimeException`, it is logged at `SEVERE` and swallowed. The save or draft change still completes.
- See [Lifecycle](../concepts/lifecycle.md) for the full state diagram.

## Failure behavior

| Condition | Exception | Propagates? | Notes |
| --- | --- | --- | --- |
| Numeric range invalid (min > max, step <= 0, default out of range) | `IllegalArgumentException` | Yes | Thrown at construction time. |
| `StringOption` maximumLength < 1 or default too long | `IllegalArgumentException` | Yes | Thrown at construction time. |
| `SelectorOption` choices empty or default not in choices | `IllegalArgumentException` | Yes | Thrown at construction time. |
| `ListOption` size range invalid or default out of range | `IllegalArgumentException` | Yes | Thrown at construction time. |
| `KeybindOption` default key does not match the allowed inputs | `IllegalArgumentException` | Yes | Thrown at construction time. |
| `validateWith` rejects a value during `setDraftValue` | `IllegalArgumentException` | Yes | `validationError` is set to the validator's error text. |
| Option-specific `validate()` (range/format) throws during `setDraftValue` | `IllegalArgumentException` | Yes | `validationError` is **not** set — it stays at its previous value (usually cleared at entry). |
| `onChanged` callback throws | None — logged at `SEVERE` | No | Draft value still changes. |
| `onSaved` callback throws | None — logged at `SEVERE` | No | Save still completes. |

## Variations

### Builder convenience methods

The builder provides shorthand for every option type. These create the option internally and add it to the current category:

```java
builder.booleanOption("enabled", "Enabled", true)
       .integerOption("volume", "Volume", 50, 0, 100)
       .doubleOption("opacity", "Opacity", 0.5, 0, 1)
       .stringOption("name", "Name", "Player")
       .enumOption("mode", "Mode", Mode.SIMPLE, Mode.class)
       .colorOption("accent", ConfigText.literal("Accent"), ConfigText.empty(), 0xFF0000, false)
       .keybindOption("hotkey", ConfigText.literal("Hotkey"), ConfigText.empty(), "key.keyboard.g")
       .buttonOption("reload", "Reload", () -> {})
       .descriptionEntry("hint", ConfigText.literal("This is a hint"));
```

When you use convenience methods, you cannot chain metadata methods. To add validation, tooltips, or callbacks, construct the option directly and use `addOption(...)`.

### Subcategory nesting

`SubcategoryOption` children participate in validation, reset, search, and persistence. Children can themselves be `SubcategoryOption` for arbitrary nesting depth. A subcategory's own expansion state is not persistent.

## Common mistakes

- Calling `validateWith` after the option is already registered. The validator is applied immediately to the current value and draft value, so it works, but if the current value is invalid, `validateValue` throws and the call fails. Set validators before registration.
- Forgetting that `EnumOption.encode()` returns the enum name string, not the ordinal. Storage backends must handle string values.
- Using `ListOption` without a `newElementFrom` supplier and expecting a typed default. Without a supplier, `newElementText()` returns `""` and `newElement()` returns `codec.decode("")`.
- Setting `editable(false)` on the builder via `builder.editable(false)` instead of on individual options. The builder method makes all options in all categories read-only.

## Related API

- [Conditional Entries](conditional-entries.md) — `ConfigRequirement` in depth.
- [Presets](presets.md) — named value batches.
- [Lifecycle](../concepts/lifecycle.md) — draft, value, save, and callback timing.
- [Options Reference](../reference/options.md)
