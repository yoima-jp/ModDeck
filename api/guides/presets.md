---
title: Presets Guide
summary: Define named value batches that users can apply to draft values without saving.
audience:
  - mod-developer
  - coding-agent
environment:
  - common
related_packages:
  - com.yoima.moddeck.api
last_verified_commit: "4790b77de8b5c728d89099e1ac7637c4b99f1b29"
---

# Presets Guide

## Goal

Register named presets that apply several typed draft values at once. Presets update drafts only; the user must still save explicitly.

## Use this when

- You want to offer "Performance", "Quality", or "Minimal" quick-set profiles.
- You need to set multiple options atomically as a batch.

## Do not use this when

- You want to save values programmatically. Use `ConfigScreenApi.save` instead.
- You want to set a single option. Call `setDraftValue` directly.

## Prerequisites

- Read [Registering Options](registering-options.md) first.
- Source set: **common** (`src/main/java`).

## Complete example

```java
package com.example.mymod;

import com.yoima.moddeck.api.ConfigDefinition;
import com.yoima.moddeck.api.ConfigPreset;
import com.yoima.moddeck.api.ConfigScreenApi;
import com.yoima.moddeck.api.ConfigText;
import com.yoima.moddeck.api.option.BooleanOption;
import com.yoima.moddeck.api.option.IntegerOption;

public final class PresetConfig {
    public enum ParticleMode { ALL, MINIMAL, OFF }
    public enum RenderMode { FAST, BALANCED, QUALITY }

    public static void register() {
        ConfigPreset performance = ConfigPreset.builderKey(
                "performance", "mymod.preset.performance")
            .set("video", "particles", ParticleMode.MINIMAL)
            .set("video", "render_distance", 8)
            .set("video", "vsync", true)
            .build();

        ConfigPreset quality = ConfigPreset.builderKey(
                "quality", "mymod.preset.quality")
            .set("video", "particles", ParticleMode.ALL)
            .set("video", "render_distance", 16)
            .set("video", "vsync", true)
            .build();

        ConfigScreenApi.register(
            ConfigDefinition.builder("mymod")
                .titleKey("mymod.config.title")
                .categoryKey("video", "mymod.category.video")
                .enumOption("particles",
                    ConfigText.translatable("mymod.option.particles"),
                    ConfigText.translatable("mymod.option.particles.desc"),
                    ParticleMode.ALL, ParticleMode.class)
                .integerOption("render_distance",
                    ConfigText.translatable("mymod.option.render_distance"),
                    ConfigText.translatable("mymod.option.render_distance.desc"),
                    16, 2, 32, 1)
                .booleanOptionKey("vsync", "mymod.option.vsync",
                    "mymod.option.vsync.desc", true)
                .preset(performance)
                .preset(quality)
                .build()
        );
    }
}
```

## How it works

1. `ConfigPreset.builder(id, displayText)` or `builderKey(id, translationKey)` creates a preset builder.
2. `set(categoryId, optionId, value)` adds an entry. The value is the typed Java object that `ConfigOption.setDraftValue` would accept — not the encoded/storage form.
3. `build()` validates that the preset has at least one entry and no duplicate targets.
4. `ConfigDefinition.Builder.preset(preset)` adds the preset to the definition. At `build()` time, the definition validates that every preset target references an existing, persistent option.
5. `definition.applyPreset(id)` validates all entries as a batch before applying any. If any entry is invalid, no draft values change. If all pass, each entry's value is set as a draft via `setDraftValue`, which triggers validation and `onChanged` callbacks.

## Lifecycle and side effects

- `applyPreset` only changes `draftValue`. It does not call `onSaved` or the definition's save callback.
- `applyPreset` triggers `onChanged` for each option whose draft actually changes.
- After applying, `definition.isDirty()` returns true if any draft differs from its saved value.
- The user must save explicitly to persist the preset values.

## Failure behavior

| Condition | Exception | Propagates? | State changed? |
| --- | --- | --- | --- |
| Unknown preset ID | `IllegalArgumentException` | Yes | No |
| Preset targets unknown category (at `build()`) | `IllegalArgumentException` | Yes | No |
| Preset targets unknown option (at `build()`) | `IllegalArgumentException` | Yes | No |
| Preset targets non-persistent option (at `build()`) | `IllegalArgumentException` | Yes | No |
| Duplicate preset ID (at `build()`) | `IllegalArgumentException` | Yes | No |
| Preset value type incompatible with option (at `applyPreset`) | `IllegalArgumentException` | Yes | No — batch validation prevents partial application |
| Preset value fails validation (at `applyPreset`) | `IllegalArgumentException` | Yes | No — batch validation prevents partial application |
| Empty preset (no entries) at `build()` | `IllegalStateException` | Yes | No |
| Duplicate target within preset at `set()` | `IllegalArgumentException` | Yes | No |

## Variations

### Preset targeting nested subcategory options

Preset entries use category and option IDs. For options nested inside a `SubcategoryOption`, the category ID is still the top-level category — the option ID is the child's ID:

```java
ConfigPreset preset = ConfigPreset.builder("custom", "Custom")
    .set("general", "child_option", "preset_value")
    .build();
```

### Multiple presets

A definition can have multiple presets. They coexist and are listed by `definition.presets()`.

## Common mistakes

- Passing encoded string values instead of typed Java objects. `set("general", "mode", "DETAILED")` is wrong for an enum option — pass `Mode.DETAILED` instead.
- Expecting `applyPreset` to save. It only sets drafts.
- Forgetting that preset targets are validated at definition `build()` time, but value compatibility is validated at `applyPreset` time. A preset that references a valid option but passes an out-of-range value builds successfully but throws when applied.

## Related API

- [Registering Options](registering-options.md)
- [Lifecycle](../concepts/lifecycle.md)
- [Presets Reference](../reference/presets.md)