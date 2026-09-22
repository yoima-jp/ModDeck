---
title: ModDeck API Documentation
summary: Entry point for developers and AI agents using the ModDeck declarative config-screen library.
audience:
  - mod-developer
  - coding-agent
environment:
  - common
  - client
last_verified_commit: "4790b77de8b5c728d89099e1ac7637c4b99f1b29"
---

# ModDeck API Documentation

ModDeck is a declarative configuration-screen library for Minecraft Java Edition 26.3 and Fabric.
Mods register typed options through the public API; ModDeck generates a consistent screen and
persists values without requiring each mod to implement Minecraft widgets or file I/O.

The public API lives under `com.yoima.moddeck.api`. Minecraft client classes are isolated in the
`src/client` source set so the common entrypoint remains dedicated-server safe.

## What do you want to do?

| What you want to do | Start reading | Main APIs |
| --- | --- | --- |
| Set up ModDeck for the first time in my mod | [Getting Started](guides/getting-started.md) | `ConfigScreenApi`, `ConfigDefinition.Builder` |
| Register a config screen with typed options | [Registering Options](guides/registering-options.md) | `ConfigDefinition.Builder`, all `*Option` types |
| Use annotation-driven registration instead of a builder | [AutoConfig Guide](guides/auto-config.md) | `@ModDeckAutoConfig`, `@AutoEntry`, `AutoConfig` |
| Make some entries appear or become enabled based on other values | [Conditional Entries](guides/conditional-entries.md) | `ConfigRequirement`, `enabledWhen`, `displayedWhen` |
| Define named presets that users can apply | [Presets Guide](guides/presets.md) | `ConfigPreset.Builder`, `ConfigDefinition.applyPreset` |
| Persist, load, or replace the storage backend | [Saving and Loading](guides/saving-and-loading.md) | `ConfigStorage`, `JsonConfigStorage`, `ConfigScreenApi.useStorage` |
| Open a config screen from client code or Mod Menu | [Client Screens](guides/client-screens.md) | `ModDeckApi`, `ConfigRoute` |
| Create a custom option type with its own widget | [Custom Options](guides/custom-options.md) | `ConfigOption<T>`, `OptionWidgetRegistry`, `ValueCodec` |
| Understand when draft values change, when saves happen, and what callbacks fire | [Lifecycle](concepts/lifecycle.md) | `ConfigOption`, `ConfigDefinition` |
| Understand the boundary between Common code and Client-only code | [Client/Common Boundary](concepts/client-common-boundary.md) | Source sets, `ModDeckApi`, `OptionWidgetRegistry` |
| Look up exact signatures and behavior of a specific type | [Reference](reference/) | All types under `com.yoima.moddeck.api` |

## Directory layout

- `guides/` — Task-oriented walkthroughs with complete examples.
- `concepts/` — Explanations of ideas that span multiple APIs.
- `reference/` — Exact signatures, behavior, failure modes, and constraints for every public type.
- `examples/` — Self-contained, compilable code samples.
- `AGENTS.md` — Rules for AI agents that read or update this documentation.

## Quick example

```java
import com.yoima.moddeck.api.ConfigDefinition;
import com.yoima.moddeck.api.ConfigScreenApi;
import com.yoima.moddeck.api.ConfigText;

ConfigScreenApi.register(
    ConfigDefinition.builder("example_mod")
        .titleKey("example_mod.config.title")
        .categoryKey("general", "example_mod.category.general")
        .booleanOptionKey("enabled", "example_mod.option.enabled",
            "example_mod.option.enabled.description", true)
        .integerOption("volume",
            ConfigText.translatable("example_mod.option.volume"),
            ConfigText.translatable("example_mod.option.volume.description"),
            50, 0, 100, 1)
        .build()
);
```

## Requirements

- Minecraft Java Edition 26.3
- Fabric Loader 0.19.5 or newer
- Fabric API 0.161.0+26.3 or newer
- Java 25

ModDeck is published on the Modrinth Maven repository (`https://api.modrinth.com/maven`). Add it to your `build.gradle` as `implementation 'maven.modrinth:mod-deck:0.1.0'` (standard `implementation`, never `modImplementation`, because this is a non-obfuscated Fabric 26.3 project) and declare `moddeck` as a dependency in your `fabric.mod.json`. See [Getting Started](guides/getting-started.md) for the full setup.

## For AI agents

If you are an AI coding agent working with ModDeck, read [AGENTS.md](AGENTS.md) first.

## Links

- Modrinth: <https://modrinth.com/mod/mod-deck>
- Source: <https://github.com/yoima-jp/ModDeck>
- Issues: <https://github.com/yoima-jp/ModDeck/issues>
