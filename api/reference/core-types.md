---
title: Core Types Reference
summary: ConfigScreenApi, ConfigRegistry, ConfigDefinition, ConfigCategory, ConfigRoute, ConfigText, ConfigScreenStyle, ConfigRequirement, OptionPresentation.
audience:
  - mod-developer
  - coding-agent
environment:
  - common
related_packages:
  - com.yoima.moddeck.api
last_verified_commit: "4790b77de8b5c728d89099e1ac7637c4b99f1b29"
---

# Core Types Reference

## ConfigScreenApi

**Package:** `com.yoima.moddeck.api`
**Environment:** common
**Source set:** `src/main/java`
**Kind:** final class, static methods only

### Purpose

Primary entry point for other mods. Registers definitions, manages storage, and triggers saves.

### Methods

#### `static void register(ConfigDefinition definition)`

Registers a definition in `ConfigRegistry`. If a `ConfigStorage` is installed, loads persisted values immediately. Load failures are logged at `WARNING` and defaults remain active.

Throws `IllegalStateException` if the mod ID or route is already registered.

#### `static void useStorage(ConfigStorage newStorage)`

Installs or replaces the persistence backend. All currently registered definitions are loaded from the new storage immediately. Per-definition load failures are logged.

Throws `NullPointerException` if `newStorage` is null.

#### `static Optional<ConfigStorage> storage()`

Returns the currently installed storage, or empty if none has been installed.

#### `static ConfigRoute route(String modId)`

Returns the `ConfigRoute` for a registered mod ID.

Throws `IllegalArgumentException` if no definition is registered for the mod ID.

#### `static void save(ConfigDefinition definition) throws IOException`

Persists the definition through the installed storage, then calls `definition.notifySaved()` to commit drafts and fire callbacks.

Throws:
- `NullPointerException` if definition is null.
- `IllegalStateException` if `definition.isValid()` is false.
- `IOException` if no storage is installed or if the storage backend throws.

---

## ConfigRegistry

**Package:** `com.yoima.moddeck.api`
**Environment:** common
**Source set:** `src/main/java`
**Kind:** final class, static methods only

### Purpose

Thread-safe central registry for `ConfigDefinition` instances. Uses `ConcurrentHashMap`.

### Methods

#### `static void register(ConfigDefinition definition)`

Adds the definition. Rejects duplicate mod IDs and duplicate routes with `IllegalStateException`. Never overwrites an existing entry.

#### `static Optional<ConfigDefinition> get(String modId)`

Returns the definition for the given mod ID, or empty.

#### `static Optional<ConfigDefinition> get(ConfigRoute route)`

Returns the definition for the given route, or empty.

#### `static List<ConfigDefinition> getAll()`

Returns all registered definitions, sorted by mod ID.

---

## ConfigDefinition

**Package:** `com.yoima.moddeck.api`
**Environment:** common
**Source set:** `src/main/java`
**Kind:** final class, immutable structure with mutable option values

### Purpose

Immutable screen structure containing mutable typed option values. Built via `ConfigDefinition.Builder`.

### Methods

| Method | Returns | Notes |
| --- | --- | --- |
| `modId()` | `String` | |
| `route()` | `ConfigRoute` | |
| `titleText()` | `ConfigText` | |
| `descriptionText()` | `ConfigText` | |
| `categories()` | `List<ConfigCategory>` | |
| `style()` | `ConfigScreenStyle` | |
| `isValid()` | `boolean` | True if no option has a validation error. |
| `isDirty()` | `boolean` | True if any option is dirty. |
| `option(String categoryId, String optionId)` | `Optional<ConfigOption<?>>` | Searches top-level and nested subcategory options. |
| `reset()` | `void` | Resets all options to defaults (draft only). |
| `discardChanges()` | `void` | Restores all drafts to last saved/loaded values. |
| `presets()` | `List<ConfigPreset>` | |
| `preset(String id)` | `Optional<ConfigPreset>` | |
| `applyPreset(String presetId)` | `void` | See [Presets Reference](presets.md). |

#### Deprecated methods

| Method | Replacement |
| --- | --- |
| `title()` | `titleText().component().getString()` or `titleText().value()` |
| `description()` | `descriptionText().component().getString()` or `descriptionText().value()` |

### `ConfigDefinition.Builder`

Created via `ConfigDefinition.builder(String modId)`. The `modId` must match `[a-z0-9_.-]+`.

#### Category methods

| Method | Notes |
| --- | --- |
| `category(String id, String displayName)` | Literal name, auto-incremented order. |
| `categoryKey(String id, String translationKey)` | Translatable name, auto-incremented order. |
| `category(String id, ConfigText displayName)` | ConfigText name, auto-incremented order. |
| `category(String id, ConfigText displayName, int order)` | Explicit order. |

Category IDs must match `[a-z0-9_.-]+`. Duplicate category IDs throw `IllegalArgumentException`.

#### Option convenience methods

All methods add an option to the current category. Calling before `category(...)` throws `IllegalStateException`.

| Method | Option type created |
| --- | --- |
| `booleanOption(id, name, defaultValue)` | `BooleanOption` |
| `booleanOption(id, name, description, defaultValue)` | `BooleanOption` |
| `booleanOption(id, ConfigText name, ConfigText desc, defaultValue)` | `BooleanOption` |
| `booleanOptionKey(id, nameKey, descKey, defaultValue)` | `BooleanOption` |
| `integerOption(id, name, defaultValue, min, max)` | `IntegerOption` (step=1) |
| `integerOption(id, name, desc, defaultValue, min, max, step)` | `IntegerOption` |
| `integerOption(id, ConfigText name, ConfigText desc, defaultValue, min, max, step)` | `IntegerOption` |
| `longOption(id, ConfigText name, ConfigText desc, defaultValue, min, max, step)` | `LongOption` |
| `floatOption(id, ConfigText name, ConfigText desc, defaultValue, min, max, step)` | `FloatOption` |
| `doubleOption(id, name, defaultValue, min, max)` | `DoubleOption` (step=0.01) |
| `doubleOption(id, name, desc, defaultValue, min, max, step)` | `DoubleOption` |
| `doubleOption(id, ConfigText name, ConfigText desc, defaultValue, min, max, step)` | `DoubleOption` |
| `stringOption(id, name, defaultValue)` | `StringOption` (maxLength=256) |
| `stringOption(id, name, desc, defaultValue, maxLength)` | `StringOption` |
| `stringOption(id, ConfigText name, ConfigText desc, defaultValue, maxLength)` | `StringOption` |
| `enumOption(id, name, defaultValue, type)` | `EnumOption<E>` |
| `enumOption(id, name, desc, defaultValue, type)` | `EnumOption<E>` |
| `enumOption(id, ConfigText name, ConfigText desc, defaultValue, type)` | `EnumOption<E>` |
| `colorOption(id, ConfigText name, ConfigText desc, defaultValue, alpha)` | `ColorOption` |
| `keybindOption(id, ConfigText name, ConfigText desc, defaultKey)` | `KeybindOption` |
| `keybindOption(id, ConfigText name, ConfigText desc, defaultKey, allowedInputs, allowUnbound)` | `KeybindOption` |
| `selectorOption(id, ConfigText name, ConfigText desc, defaultValue, choices, codec, labelFactory)` | `SelectorOption<T>` |
| `listOption(id, ConfigText name, ConfigText desc, defaultValue, codec, minSize, maxSize)` | `ListOption<T>` |
| `buttonOption(id, name, action)` | `ButtonOption` |
| `buttonOption(id, ConfigText name, ConfigText desc, ConfigText buttonText, action)` | `ButtonOption` |
| `descriptionEntry(id, ConfigText text)` | `DescriptionOption` |
| `addOption(ConfigOption<?>)` | Adds any pre-constructed option. |

#### Other builder methods

| Method | Notes |
| --- | --- |
| `route(ConfigRoute)` | Overrides the default route. |
| `title(String)` / `titleKey(key, args...)` / `title(ConfigText)` | Sets the title. |
| `description(String)` / `descriptionKey(key, args...)` / `description(ConfigText)` | Sets the description. |
| `style(ConfigScreenStyle)` | Sets screen style. |
| `onSave(Runnable)` | Callback after successful save. |
| `editable(boolean)` | Makes all options read-only when false. |
| `preset(ConfigPreset)` | Adds a preset. Duplicate preset IDs throw. |

#### `build()`

Validates and creates the `ConfigDefinition`. Throws `IllegalStateException` if no category is added. Validates preset targets against the built category/option structure.

---

## ConfigCategory

**Package:** `com.yoima.moddeck.api`
**Kind:** final class, immutable

| Method | Returns |
| --- | --- |
| `id()` | `String` |
| `displayNameText()` | `ConfigText` |
| `order()` | `int` |
| `options()` | `List<ConfigOption<?>>` |

Deprecated: `displayName()` — use `displayNameText()`.

---

## ConfigRoute

**Package:** `com.yoima.moddeck.api`
**Kind:** record `(String namespace, String path)`

| Method | Returns | Notes |
| --- | --- | --- |
| `forMod(String modId)` | `ConfigRoute` | Creates `moddeck:config/<modId>`. |
| `parse(String route)` | `ConfigRoute` | Parses `namespace:path` syntax. |
| `toString()` | `String` | Returns `namespace:path`. |

Both namespace and path must match their respective patterns. Namespace: `[a-z0-9_.-]+`. Path: `[a-z0-9/._-]+`, no leading or trailing `/`.

---

## ConfigText

**Package:** `com.yoima.moddeck.api`
**Kind:** final class, immutable

### Purpose

Language-neutral UI text. Translation keys are resolved only at render time.

| Method | Returns | Notes |
| --- | --- | --- |
| `empty()` | `ConfigText` | Empty literal. |
| `literal(String text)` | `ConfigText` | Fixed text. |
| `translatable(String key, Object... args)` | `ConfigText` | Translation key. Blank key throws. |
| `value()` | `String` | The raw key or literal text. |
| `isTranslationKey()` | `boolean` | |
| `arguments()` | `Object[]` | Cloned array. |
| `component()` | `Component` | Fresh `Component` each call. |
| `isEmpty()` | `boolean` | |

---

## ConfigScreenStyle

**Package:** `com.yoima.moddeck.api`
**Kind:** record

| Field | Type | Notes |
| --- | --- | --- |
| `transparentBackground` | `boolean` | Retained for source compatibility. Ignored by the renderer. |
| `backgroundTexture` | `String` | Validated as `namespace:path` if non-empty. Retained for source compatibility. Ignored by the renderer. |
| `accentColor` | `int` | 0 keeps the theme accent. |
| `confirmSave` | `boolean` | |

`DEFAULT = new ConfigScreenStyle(false, "", 0, false)`

Of these fields, only `accentColor` and `confirmSave` affect the current client renderer. `transparentBackground` and `backgroundTexture` are retained for source compatibility and ignored at render time.

---

## ConfigRequirement

**Package:** `com.yoima.moddeck.api`
**Kind:** `@FunctionalInterface` interface

| Method | Returns | Notes |
| --- | --- | --- |
| `test()` | `boolean` | Evaluate the condition. |
| `isTrue(BooleanOption)` | `ConfigRequirement` | |
| `isFalse(BooleanOption)` | `ConfigRequirement` | |
| `isValue(ConfigOption<T>, T first, T... remaining)` | `ConfigRequirement` | |
| `matches(ConfigOption<T>, ConfigOption<T>)` | `ConfigRequirement` | |
| `not(ConfigRequirement)` | `ConfigRequirement` | |
| `all(ConfigRequirement...)` | `ConfigRequirement` | |
| `any(ConfigRequirement...)` | `ConfigRequirement` | |
| `none(ConfigRequirement...)` | `ConfigRequirement` | |
| `one(ConfigRequirement...)` | `ConfigRequirement` | Exactly one. |

---

## OptionPresentation

**Package:** `com.yoima.moddeck.api`
**Kind:** enum

Values: `AUTOMATIC`, `TOGGLE`, `SLIDER`, `NUMBER_FIELD`, `TEXT_FIELD`, `SELECTOR`, `DROPDOWN`, `COLOR`, `KEYBIND`, `LIST`, `SUBCATEGORY`, `DESCRIPTION`, `BUTTON`, `CUSTOM`.
