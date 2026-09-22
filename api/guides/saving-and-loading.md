---
title: Saving and Loading
summary: How ModDeck persists config values, and how to replace the storage backend.
audience:
  - mod-developer
  - coding-agent
environment:
  - common
related_packages:
  - com.yoima.moddeck.api.storage
  - com.yoima.moddeck.storage
last_verified_commit: "4790b77de8b5c728d89099e1ac7637c4b99f1b29"
---

# Saving and Loading

## Goal

Understand how ModDeck persists config values, how to load them, and how to install a custom storage backend.

## Use this when

- You need to know when values are written to disk and what format is used.
- You want to replace `JsonConfigStorage` with your own `ConfigStorage` implementation.
- You need to call save or load programmatically.

## Do not use this when

- You just want to register a config screen. Registration handles loading automatically.
- You want to open a screen. See [Client Screens](client-screens.md).

## Prerequisites

- Read [Getting Started](getting-started.md) first.
- Source set: **common** (`src/main/java`).

## Built-in storage

ModDeck installs `JsonConfigStorage` during `ModDeck.onInitialize()`, pointing at `config/moddeck/`. Each registered definition gets its own file: `config/moddeck/<modId>.json`.

### JSON format

```json
{
  "general": {
    "enabled": false,
    "volume": 75,
    "opacity": 0.8,
    "name": "Alex",
    "mode": "COMPACT"
  },
  "lists": {
    "tags": ["one", "two"],
    "nested": {
      "limit": 42
    }
  }
}
```

- Top-level keys are category IDs.
- Each category contains option IDs mapped to their encoded values.
- `SubcategoryOption` children are nested under the subcategory's ID as a JSON object.
- Non-persistent options (`DescriptionOption`, `ButtonOption`, `SubcategoryOption` itself) are not written.
- `EnumOption` values are stored as the enum constant name string.
- `ColorOption` values are stored as integers.
- `ListOption` values are stored as JSON arrays of strings (each element encoded by the `ValueCodec`).
- `SelectorOption` values are stored as strings (encoded by the `ValueCodec`).
- `KeybindOption` values are stored as the Minecraft key identifier string.

### Atomic writes

`JsonConfigStorage.save` writes to a temporary file first, then performs an atomic move. If the filesystem does not support atomic moves, it falls back to a non-atomic replace.

## Complete example: programmatic save

```java
package com.example.mymod;

import com.yoima.moddeck.api.ConfigScreenApi;
import com.yoima.moddeck.api.ConfigDefinition;
import com.yoima.moddeck.api.option.IntegerOption;

import java.io.IOException;

public final class ConfigSaveExample {
    public static void saveIfChanged(ConfigDefinition definition) throws IOException {
        if (definition.isDirty()) {
            ConfigScreenApi.save(definition);
        }
    }
}
```

## Complete example: custom storage backend

```java
package com.example.mymod;

import com.yoima.moddeck.api.ConfigDefinition;
import com.yoima.moddeck.api.storage.ConfigStorage;

import com.yoima.moddeck.api.ConfigCategory;
import com.yoima.moddeck.api.option.ConfigOption;
import com.yoima.moddeck.api.option.SubcategoryOption;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonParseException;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Minimal JSON storage backend that mirrors the structure JsonConfigStorage writes:
 * top-level category IDs, each containing option IDs mapped to their encoded values.
 * Subcategory children are nested under the subcategory's ID.
 */
public final class MyJsonStorage implements ConfigStorage {
    private static final Logger LOGGER = Logger.getLogger(MyJsonStorage.class.getName());
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path directory;

    public MyJsonStorage(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
    }

    @Override
    public void load(ConfigDefinition definition) throws IOException {
        Path file = fileFor(definition);
        if (!Files.exists(file)) return;
        JsonObject root;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                throw new IOException("Config root must be a JSON object: " + file);
            }
            root = parsed.getAsJsonObject();
        } catch (JsonParseException exception) {
            throw new IOException("Invalid JSON in " + file, exception);
        }
        for (ConfigCategory category : definition.categories()) {
            if (!root.has(category.id()) || !root.get(category.id()).isJsonObject()) continue;
            JsonObject categoryJson = root.getAsJsonObject(category.id());
            loadOptions(category.options(), categoryJson);
        }
    }

    private void loadOptions(java.util.List<ConfigOption<?>> options, JsonObject parentJson) {
        for (ConfigOption<?> option : options) {
            if (option instanceof SubcategoryOption subcategory) {
                if (!parentJson.has(subcategory.id()) || !parentJson.get(subcategory.id()).isJsonObject()) continue;
                loadOptions(subcategory.children(), parentJson.getAsJsonObject(subcategory.id()));
                continue;
            }
            if (!option.persistent()) continue;
            if (!parentJson.has(option.id()) || parentJson.get(option.id()).isJsonNull()) continue;
            try {
                option.loadEncodedValue(storageValue(parentJson.get(option.id())));
            } catch (RuntimeException exception) {
                LOGGER.log(Level.WARNING, "Ignoring invalid value for option " + option.id(), exception);
            }
        }
    }

    private static Object storageValue(JsonElement element) {
        if (element.isJsonArray()) {
            List<Object> values = new ArrayList<>();
            for (JsonElement child : element.getAsJsonArray()) {
                if (child.isJsonNull()) {
                    throw new IllegalArgumentException("Null list elements are not supported");
                }
                values.add(storageValue(child));
            }
            return List.copyOf(values);
        }
        if (!element.isJsonPrimitive()) {
            throw new IllegalArgumentException("Expected primitive or array JSON value");
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isBoolean()) return primitive.getAsBoolean();
        if (primitive.isNumber()) return primitive.getAsNumber();
        return primitive.getAsString();
    }

    @Override
    public void save(ConfigDefinition definition) throws IOException {
        Files.createDirectories(directory);
        JsonObject root = new JsonObject();
        for (ConfigCategory category : definition.categories()) {
            JsonObject categoryJson = new JsonObject();
            saveOptions(category.options(), categoryJson);
            root.add(category.id(), categoryJson);
        }
        Path target = fileFor(definition);
        Path temp = Files.createTempFile(directory, definition.modId() + "-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING)) {
                GSON.toJson(root, writer);
            }
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private void saveOptions(java.util.List<ConfigOption<?>> options, JsonObject parentJson) {
        for (ConfigOption<?> option : options) {
            if (option instanceof SubcategoryOption subcategory) {
                JsonObject childJson = new JsonObject();
                saveOptions(subcategory.children(), childJson);
                parentJson.add(subcategory.id(), childJson);
                continue;
            }
            if (!option.persistent()) continue;
            parentJson.add(option.id(), GSON.toJsonTree(option.encodeDraft()));
        }
    }

    private Path fileFor(ConfigDefinition definition) {
        Path file = directory.resolve(definition.modId() + ".json").normalize();
        if (!file.getParent().equals(directory)) {
            throw new IllegalArgumentException("Unsafe mod id: " + definition.modId());
        }
        return file;
    }
}
```

Install it before registering definitions:

```java
package com.example.mymod;

import com.yoima.moddeck.api.ConfigScreenApi;
import com.yoima.moddeck.api.ConfigDefinition;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public final class MyModInitializer implements ModInitializer {
    @Override
    public void onInitialize() {
        ConfigScreenApi.useStorage(new MyJsonStorage(
            FabricLoader.getInstance().getConfigDir().resolve("mymod")
        ));
        ConfigScreenApi.register(
            ConfigDefinition.builder("mymod")
                .titleKey("mymod.config.title")
                .categoryKey("general", "mymod.category.general")
                .booleanOptionKey(
                    "enabled",
                    "mymod.option.enabled",
                    "mymod.option.enabled.description",
                    true)
                .build()
        );
    }
}
```

Minecraft 26.3 supplies Gson on the mod compile and runtime classpaths used by this project.

## How it works

### Registration and loading

When `ConfigScreenApi.register(definition)` is called:

1. The definition is added to `ConfigRegistry`.
2. If a `ConfigStorage` is installed, `storage.load(definition)` is called.
3. If load throws `IOException` or `RuntimeException`, it is logged at `WARNING` and defaults remain active. The definition is still registered.

### `useStorage`

When `ConfigScreenApi.useStorage(newStorage)` is called:

1. The new storage replaces the previous one.
2. All currently registered definitions are loaded from the new storage.
3. Load failures are logged per-definition and do not prevent other definitions from loading.

### Save flow

When `ConfigScreenApi.save(definition)` is called:

1. `definition.isValid()` is checked. If any option has a validation error, `IllegalStateException` is thrown.
2. `storage.save(definition)` is called. This must persist `encodeDraft()` values.
3. `definition.notifySaved()` is called, which:
   a. For each option, calls `option.notifySaved()`, setting `value = draftValue` and invoking `onSaved` consumers.
   b. Invokes the definition's `onSave` callback (set via `builder.onSave(Runnable)`).

### `ConfigStorage` interface

```java
public interface ConfigStorage {
    void load(ConfigDefinition definition) throws IOException;
    void save(ConfigDefinition definition) throws IOException;

    default void reset(ConfigDefinition definition) throws IOException {
        definition.reset();
        save(definition);
    }
}
```

`reset` has a default implementation that resets all options to their defaults and then saves. Custom backends can override it.
The default `reset` bypasses `ConfigScreenApi.save` entirely — it calls `definition.reset()` (which only changes drafts) and then `save(definition)` directly. As a result:
- Drafts are written to disk as default values.
- `value()` is not committed to the default — it stays at whatever it was before the call.
- Option-level `onSaved` callbacks do not fire.
- Definition-level `onSave` callback does not fire.

To commit defaults as the active value and fire all callbacks, call `definition.reset()` first, then `ConfigScreenApi.save(definition)`:

```java
package com.example.mymod;

import com.yoima.moddeck.api.ConfigDefinition;
import com.yoima.moddeck.api.ConfigScreenApi;

import java.io.IOException;

public final class ConfigResetExample {
    public void resetAndCommit(ConfigDefinition definition) throws IOException {
        definition.reset();
        ConfigScreenApi.save(definition);
    }
}
```

This flow commits the default drafts to `value()` and fires `onSaved` and `onSave` because `ConfigScreenApi.save` calls `definition.notifySaved()`.

### `loadEncodedValue` vs `setDraftValue`

- `option.loadEncodedValue(Object)` — used by storage backends during load. It decodes, validates, and sets both `value` and `draftValue` without triggering `onChanged`. Throws `IllegalArgumentException` if the decoded value is invalid.
- `option.setDraftValue(T)` — used by UI widgets and programmatic draft changes. It validates and sets `draftValue` only, triggering `onChanged` if the value changed.

## Lifecycle and side effects

- `load` sets both `value()` and `draftValue()` to the loaded value. No callbacks fire.
- `save` sets `value()` to the current `draftValue()` for each option, then fires `onSaved` consumers and the definition's `onSave` callback.
- If `onSaved` or `onSave` throws, the exception is logged at `SEVERE` and swallowed. The save still completes.
- `JsonConfigStorage` ignores invalid values per-option during load, logging a `WARNING`. Other options in the same file are still loaded.

## Failure behavior

| Condition | Exception | Propagates? | State changed? |
| --- | --- | --- | --- |
| `save` called with no storage installed | `IOException` | Yes | No |
| `save` called when `isValid()` is false | `IllegalStateException` | Yes | No |
| `save` I/O failure | `IOException` | Yes | No — `notifySaved` is not called |
| `load` file not found | None — returns silently | No | Defaults remain |
| `load` malformed JSON | `IOException` | Depends on caller | Defaults remain for all options |
| `load` invalid value for one option | None — logged at `WARNING` | No | That option keeps default; others load |
| `loadEncodedValue` rejects decoded value | `IllegalArgumentException` | Caught by `JsonConfigStorage`, logged | That option keeps default |
| `onSave` callback throws | None — logged at `SEVERE` | No | Save already completed |

## Common mistakes

- Calling `storage.save(definition)` directly instead of `ConfigScreenApi.save(definition)`. Direct storage save does not call `notifySaved`, so `value()` stays stale and `onSaved` callbacks never fire.
- Installing a custom storage after registering definitions. `useStorage` does load all existing definitions, but if you register before any storage is installed, the first load happens during `register` (with no storage, so defaults are used). Install storage first, or call `useStorage` which re-loads everything.
- Expecting `loadEncodedValue` to trigger `onChanged`. It does not — it is a silent load.

## Related API

- [Lifecycle](../concepts/lifecycle.md) — full draft/value state machine.
- [Getting Started](getting-started.md) — registration flow.
- [Storage Reference](../reference/storage.md)
