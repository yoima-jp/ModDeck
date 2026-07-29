---
title: Getting Started
summary: Set up ModDeck in your mod for the first time and register a basic config screen.
audience:
  - mod-developer
  - coding-agent
environment:
  - common
related_packages:
  - com.yoima.moddeck.api
last_verified_commit: "4790b77de8b5c728d89099e1ac7637c4b99f1b29"
---

# Getting Started

## Goal

Register a ModDeck config screen from your mod initializer so players can configure your mod through the Mod Deck hub or your own settings entry point.

## Use this when

- You are adding ModDeck to your mod for the first time.
- You want a basic config screen with one or more categories and typed options.

## Do not use this when

- You want annotation-driven registration instead of a builder — see [AutoConfig Guide](auto-config.md).
- You need a custom option type with its own widget — see [Custom Options](custom-options.md).
- You want to open the screen from your own button or command — see [Client Screens](client-screens.md).

## Prerequisites

- Minecraft Java Edition 26.2, Fabric Loader 0.19.3+, Fabric API 0.155.2+26.2, Java 25.
- The `com.yoima:moddeck` artifact on your mod's classpath.
- `moddeck` declared as a dependency in your `fabric.mod.json`.

## Source set

All code in this guide runs in the **common** source set (`src/main/java`). No client-only classes are needed.

## Complete example

```java
package com.example.mymod;

import com.yoima.moddeck.api.ConfigDefinition;
import com.yoima.moddeck.api.ConfigScreenApi;
import com.yoima.moddeck.api.ConfigText;

import net.fabricmc.api.ModInitializer;

public final class MyMod implements ModInitializer {
    public static final String MOD_ID = "mymod";

    @Override
    public void onInitialize() {
        ConfigScreenApi.register(
            ConfigDefinition.builder(MOD_ID)
                .titleKey("mymod.config.title")
                .descriptionKey("mymod.config.description")
                .categoryKey("general", "mymod.category.general")
                .booleanOptionKey("enabled", "mymod.option.enabled",
                    "mymod.option.enabled.description", true)
                .integerOption("volume",
                    ConfigText.translatable("mymod.option.volume"),
                    ConfigText.translatable("mymod.option.volume.description"),
                    50, 0, 100, 1)
                .categoryKey("advanced", "mymod.category.advanced")
                .doubleOption("opacity",
                    ConfigText.translatable("mymod.option.opacity"),
                    ConfigText.translatable("mymod.option.opacity.description"),
                    0.8, 0.0, 1.0, 0.05)
                .build()
        );
    }
}
```

## How it works

1. `ConfigDefinition.builder(MOD_ID)` creates a builder. The `modId` must match `[a-z0-9_.-]+` and must be unique across all registered definitions.
2. `categoryKey(...)` starts a new category. All options added after it belong to that category until the next `category(...)` call.
3. Each `*Option(...)` call adds a typed option to the current category. Option IDs must be unique within their category.
4. `build()` validates the structure: at least one category is required, option IDs must not collide, and presets (if any) must reference existing persistent options.
5. `ConfigScreenApi.register(definition)` adds the definition to the central `ConfigRegistry`. If a `ConfigStorage` is already installed (ModDeck installs `JsonConfigStorage` during its own initialization), the stored values are loaded immediately and replace the defaults.

## Lifecycle and side effects

- `register` loads persisted values from `config/moddeck/<modId>.json` if the file exists. If the file is missing or contains invalid values for an option, defaults remain active for that option.
- Until a save happens, `value()` returns the default (or loaded) value and `draftValue()` returns the value being edited in the screen.
- See [Lifecycle](../concepts/lifecycle.md) for the full draft/value/save state machine.

## Failure behavior

| Condition | Exception | Propagates? | State changed? |
| --- | --- | --- | --- |
| `modId` is null or does not match `[a-z0-9_.-]+` | `IllegalArgumentException` | Yes | No |
| Duplicate mod ID already registered | `IllegalStateException` | Yes | No |
| No category added before `build()` | `IllegalStateException` | Yes | No |
| Duplicate option ID within a category | `IllegalArgumentException` | Yes | No |
| Storage load fails (I/O or parse error) | None — logged at `WARNING` | No | Defaults remain active |

## Variations

- Use `title(String)` instead of `titleKey(...)` for fixed, non-translated text.
- Use `category(String id, String displayName)` for literal category names.
- Call `onSave(Runnable)` on the builder to receive a callback after every successful save.
- Call `style(ConfigScreenStyle)` to customize accent color or confirm-save behavior.

## Common mistakes

- Calling `booleanOption(...)` before `category(...)`. The builder throws `IllegalStateException` because there is no current category.
- Using uppercase letters in mod IDs, category IDs, or option IDs. All identifiers must match `[a-z0-9_.-]+`.
- Expecting `value()` to reflect screen edits. During editing, only `draftValue()` changes. `value()` updates after a successful save.

## Related API

- [Registering Options](registering-options.md) — every built-in option type.
- [Saving and Loading](saving-and-loading.md) — storage backends and persistence.
- [Lifecycle](../concepts/lifecycle.md) — draft vs. applied values.
- [ConfigDefinition Reference](../reference/config-definition.md)
