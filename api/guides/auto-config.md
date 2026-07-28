---
title: AutoConfig Guide
summary: Annotation-driven config registration using @ModDeckAutoConfig and @AutoEntry.
audience:
  - mod-developer
  - coding-agent
environment:
  - common
related_packages:
  - com.yoima.moddeck.api.autoconfig
last_verified_commit: "4790b77de8b5c728d89099e1ac7637c4b99f1b29"
---

# AutoConfig Guide

## Goal

Register a config screen by annotating a POJO class instead of building a `ConfigDefinition` by hand. ModDeck reads the fields, generates the definition, and synchronizes the POJO on load and save.

## Use this when

- Your config is a flat set of typed fields.
- You want the simplest registration path with the least boilerplate.

## Do not use this when

- You need custom option types, subcategories, lists with custom codecs, or selector options. AutoConfig does not support these. Use the builder API instead.
- You need fine-grained control over category ordering or option metadata beyond what the annotations offer.

## Prerequisites

- Read [Getting Started](getting-started.md) first.
- Source set: **common** (`src/main/java`).

## Supported field types

| Java type | Generated option type | Additional annotation |
| --- | --- | --- |
| `boolean` / `Boolean` | `BooleanOption` | — |
| `int` / `Integer` | `IntegerOption` | `@AutoRange` required |
| `int` / `Integer` (with `@AutoColor`) | `ColorOption` | `@AutoColor` |
| `long` / `Long` | `LongOption` | `@AutoRange` required |
| `float` / `Float` | `FloatOption` | `@AutoRange` required |
| `double` / `Double` | `DoubleOption` | `@AutoRange` required |
| `String` (with `@AutoKeybind`) | `KeybindOption` | `@AutoKeybind` |
| `String` | `StringOption` | — |
| Any `Enum` | `EnumOption` | — |
| `List<String>` | `ListOption<String>` | — |

Fields without `@AutoEntry` are ignored. Fields with `@AutoIgnore` are also ignored.

## Complete example

```java
package com.example.mymod;

import com.yoima.moddeck.api.autoconfig.AutoColor;
import com.yoima.moddeck.api.autoconfig.AutoConfig;
import com.yoima.moddeck.api.autoconfig.AutoConfigHolder;
import com.yoima.moddeck.api.autoconfig.AutoEntry;
import com.yoima.moddeck.api.autoconfig.AutoKeybind;
import com.yoima.moddeck.api.autoconfig.AutoRange;
import com.yoima.moddeck.api.autoconfig.ModDeckAutoConfig;

import net.fabricmc.api.ModInitializer;

import java.util.List;

public final class MyMod implements ModInitializer {
    public static final String MOD_ID = "mymod";

    @ModDeckAutoConfig(
        modId = "mymod",
        titleKey = "mymod.config.title",
        descriptionKey = "mymod.config.description"
    )
    public static final class Config {
        @AutoEntry(nameKey = "mymod.option.enabled")
        boolean enabled = true;

        @AutoEntry(nameKey = "mymod.option.count", category = "numbers",
                   categoryKey = "mymod.category.numbers", categoryOrder = 1, order = 0)
        @AutoRange(min = 0, max = 100, step = 1)
        int count = 50;

        @AutoEntry(nameKey = "mymod.option.volume", category = "numbers",
                   categoryKey = "mymod.category.numbers", categoryOrder = 1, order = 1)
        @AutoRange(min = 0, max = 100, step = 5)
        int volume = 75;

        @AutoEntry(nameKey = "mymod.option.color", category = "appearance",
                   categoryKey = "mymod.category.appearance", categoryOrder = 2)
        @AutoColor(alpha = true)
        int color = 0xCC221144;

        @AutoEntry(nameKey = "mymod.option.hotkey", category = "appearance",
                   categoryKey = "mymod.category.appearance", categoryOrder = 2)
        @AutoKeybind(keyboard = true, mouse = true, modifiers = false, unbound = true)
        String hotkey = "key.keyboard.g";

        @AutoEntry(nameKey = "mymod.option.tags", category = "appearance",
                   categoryKey = "mymod.category.appearance", categoryOrder = 2)
        List<String> tags = List.of("example");

        @AutoEntry(nameKey = "mymod.option.mode", requiresRestart = true)
        DisplayMode mode = DisplayMode.DETAILED;
    }

    public enum DisplayMode { SIMPLE, DETAILED, COMPACT }

    private static AutoConfigHolder<Config> holder;
    private static Config activeConfig;

    @Override
    public void onInitialize() {
        holder = AutoConfig.register(new Config());
        holder.onLoad(MyMod::applyConfig)
              .onSave(MyMod::applyConfig);
    }

    public static Config config() {
        return holder.config();
    }

    private static void applyConfig(Config cfg) {
        activeConfig = cfg;
    }
}
```

## How it works

1. `AutoConfig.register(config)` scans the class for `@AutoEntry` fields.
2. Each field is mapped to a `ConfigOption` based on its type and annotations.
3. The field name is converted to an option ID by inserting underscores at camelCase boundaries and lowercasing (e.g. `myField` becomes `my_field`).
4. Categories are created from `@AutoEntry.category()` and `@AutoEntry.categoryKey()`. The first entry's `categoryOrder` sets the category's order.
5. A `ConfigDefinition` is built and registered via `ConfigScreenApi.register`.
6. After registration (which loads persisted values), the POJO fields are updated to match the loaded option values via reflection.
7. The returned `AutoConfigHolder` exposes the generated definition and allows adding load/save listeners.

## Lifecycle and side effects

- `onLoad` listeners are invoked immediately when `onLoad` is called, with the current config instance. This is an immediate registration callback, not a recurring load lifecycle hook.
- `AutoConfigHolder` does not re-invoke load listeners or synchronize the backing POJO after a later `ConfigScreenApi.useStorage(...)` reload. Perform storage selection before `AutoConfig.register(...)`; the public AutoConfig API does not provide a post-registration POJO reload operation.
- `onSave` listeners are called after `ConfigScreenApi.save` succeeds and the definition's save callback fires. The POJO fields are updated to the saved values before the listeners fire.
- The save consumer on each option writes the saved value back to the POJO field via reflection.

## Failure behavior

| Condition | Exception | Propagates? | Notes |
| --- | --- | --- | --- |
| Class missing `@ModDeckAutoConfig` | `IllegalArgumentException` | Yes | |
| No `@AutoEntry` fields found | `IllegalArgumentException` | Yes | |
| Numeric field without `@AutoRange` | `IllegalArgumentException` | Yes | |
| Unsupported field type (e.g. `Map`, custom object) | `IllegalArgumentException` | Yes | |
| Field is not accessible (security manager) | `IllegalArgumentException` | Yes | `setAccessible(true)` is called, but may fail in restricted environments. |

## Variations

### Load and save listeners

```java
AutoConfigHolder<MyModConfig> holder = AutoConfig.register(new MyModConfig());
holder.onLoad(config -> applyConfig(config))
      .onSave(config -> applyConfig(config));
```

`onLoad` is called immediately with the current config. `onSave` is called after each successful save.

### Field name to option ID conversion

| Field name | Option ID |
| --- | --- |
| `enabled` | `enabled` |
| `maxHealth` | `max_health` |
| `renderDistance` | `render_distance` |

## Common mistakes

- Forgetting `@AutoRange` on numeric fields. The registration throws `IllegalArgumentException`.
- Expecting `List<T>` for non-String element types. Only `List<String>` is supported.
- Using `@AutoColor` on a non-`int` field. The annotation is only checked for `int`/`Integer` types.
- Assuming category order is the field declaration order. Categories are ordered by `categoryOrder` from the first entry that declares them; entries within a category are ordered by `order`.

## Related API

- [Getting Started](getting-started.md) — manual builder approach.
- [Registering Options](registering-options.md) — full option type catalog.
- [Lifecycle](../concepts/lifecycle.md) — load and save timing.
- [AutoConfig Reference](../reference/auto-config.md)
