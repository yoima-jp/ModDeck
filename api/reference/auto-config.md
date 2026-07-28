---
title: AutoConfig Reference
summary: AutoConfig, AutoConfigHolder, and all annotations.
audience:
  - mod-developer
  - coding-agent
environment:
  - common
related_packages:
  - com.yoima.moddeck.api.autoconfig
last_verified_commit: "4790b77de8b5c728d89099e1ac7637c4b99f1b29"
---

# AutoConfig Reference

## AutoConfig

**Package:** `com.yoima.moddeck.api.autoconfig`
**Environment:** common
**Source set:** `src/main/java`
**Kind:** final class, static methods only

### Purpose

Reflection-based builder that converts an annotated POJO into a `ConfigDefinition` and registers it.

### Methods

#### `static <T> AutoConfigHolder<T> register(T config)`

Scans `config.getClass()` for `@ModDeckAutoConfig` and `@AutoEntry` fields. Builds a `ConfigDefinition`, registers it via `ConfigScreenApi.register`, synchronizes the POJO with loaded values, and returns an `AutoConfigHolder`.

Throws:
- `NullPointerException` if `config` is null.
- `IllegalArgumentException` if the class lacks `@ModDeckAutoConfig`.
- `IllegalArgumentException` if no `@AutoEntry` fields are found.
- `IllegalArgumentException` if a numeric field lacks `@AutoRange`.
- `IllegalArgumentException` for unsupported field types.

### Field name to option ID conversion

Field names are converted by inserting underscores at camelCase boundaries and lowercasing:

```
fieldName    → field_name
maxHealth    → max_health
renderDistance → render_distance
URL          → url (no special handling for consecutive capitals)
```

Regex: `field.getName().replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT)`

### Supported field types

| Java type | Option type | Required annotations |
| --- | --- | --- |
| `boolean` / `Boolean` | `BooleanOption` | `@AutoEntry` |
| `int` / `Integer` | `IntegerOption` | `@AutoEntry` + `@AutoRange` |
| `int` / `Integer` (with `@AutoColor`) | `ColorOption` | `@AutoEntry` + `@AutoColor` |
| `long` / `Long` | `LongOption` | `@AutoEntry` + `@AutoRange` |
| `float` / `Float` | `FloatOption` | `@AutoEntry` + `@AutoRange` |
| `double` / `Double` | `DoubleOption` | `@AutoEntry` + `@AutoRange` |
| `String` (with `@AutoKeybind`) | `KeybindOption` | `@AutoEntry` + `@AutoKeybind` |
| `String` | `StringOption` (maxLength=4096) | `@AutoEntry` |
| `Enum<E>` | `EnumOption<E>` | `@AutoEntry` |
| `List<String>` | `ListOption<String>` (codec=identity, min=0, max=MAX_VALUE) | `@AutoEntry` |

Unsupported types throw `IllegalArgumentException`.

### Save consumer

For each field, a save consumer is set on the option that writes the saved value back to the POJO field via reflection (`field.set(config, savedValue)`).

### Category and ordering

- Categories are created from `@AutoEntry.category()` and `@AutoEntry.categoryKey()`.
- Category order is determined by `@AutoEntry.categoryOrder()` from the first entry that declares the category.
- Within a category, entries are sorted by `@AutoEntry.order()`.

### Load synchronization

After `ConfigScreenApi.register` loads persisted values, the POJO fields are updated to match the loaded option values via `field.set(config, option.value())`.

---

## AutoConfigHolder<T>

**Package:** `com.yoima.moddeck.api.autoconfig`
**Kind:** final class

### Methods

| Method | Returns | Notes |
| --- | --- | --- |
| `config()` | `T` | The original POJO instance. |
| `definition()` | `ConfigDefinition` | The generated definition. |
| `onLoad(Consumer<T> listener)` | `AutoConfigHolder<T>` | Adds a load listener. Invokes the listener immediately with the current config. There is no automatic notification on subsequent loads. |
| `onSave(Consumer<T> listener)` | `AutoConfigHolder<T>` | Adds a save listener. Called after each successful save. |

### onLoad semantics

`onLoad` adds the listener to an internal list and invokes it immediately with the current config instance. The listener is not invoked again by ModDeck on later storage loads. Later `ConfigScreenApi.useStorage(...)` loads also do not re-synchronize the backing POJO. Install the intended storage before `AutoConfig.register(...)`; the public AutoConfig API does not expose a post-registration POJO reload operation.

### onSave semantics

`onSave` adds the listener to an internal list. When `ConfigScreenApi.save` succeeds, the definition-level save callback (registered internally by `AutoConfig.register`) invokes every save listener with the config instance. The POJO fields are updated to the saved values via the per-option save consumer before the save listeners fire.

### Lifecycle note

`AutoConfig.register` builds the listeners list before registration, but callers cannot add a listener until the holder is returned. Each later `onLoad(listener)` call appends that listener and immediately invokes only that newly supplied listener; previously added listeners are not re-invoked.

---

## Annotations

### @ModDeckAutoConfig

**Package:** `com.yoima.moddeck.api.autoconfig`
**Target:** `ElementType.TYPE`
**Retention:** `RUNTIME`

| Element | Type | Default | Notes |
| --- | --- | --- | --- |
| `modId` | `String` | (required) | Must match `[a-z0-9_.-]+`. |
| `titleKey` | `String` | (required) | Translation key for the config title. |
| `descriptionKey` | `String` | `""` | Translation key for the description. Empty string skips description. |

### @AutoEntry

**Package:** `com.yoima.moddeck.api.autoconfig`
**Target:** `ElementType.FIELD`
**Retention:** `RUNTIME`

| Element | Type | Default | Notes |
| --- | --- | --- | --- |
| `nameKey` | `String` | (required) | Translation key for the option name. |
| `descriptionKey` | `String` | `""` | Translation key for the description. |
| `category` | `String` | `"general"` | Category ID. |
| `categoryKey` | `String` | `"moddeck.category.general"` | Translation key for the category name. |
| `categoryOrder` | `int` | `0` | Category display order. |
| `order` | `int` | `0` | Option display order within the category. |
| `requiresRestart` | `boolean` | `false` | Marks the option as requiring a restart. |

### @AutoIgnore

**Package:** `com.yoima.moddeck.api.autoconfig`
**Target:** `ElementType.FIELD`
**Retention:** `RUNTIME`

No elements. Fields with `@AutoIgnore` are skipped during scanning. (Fields without `@AutoEntry` are also skipped, so `@AutoIgnore` is only meaningful as explicit documentation.)

### @AutoRange

**Package:** `com.yoima.moddeck.api.autoconfig`
**Target:** `ElementType.FIELD`
**Retention:** `RUNTIME`

| Element | Type | Default | Notes |
| --- | --- | --- | --- |
| `min` | `double` | (required) | Minimum value. |
| `max` | `double` | (required) | Maximum value. |
| `step` | `double` | `1` | Step size. |

Required for `int`, `long`, `float`, and `double` fields. The values are cast to the appropriate primitive type.

### @AutoColor

**Package:** `com.yoima.moddeck.api.autoconfig`
**Target:** `ElementType.FIELD`
**Retention:** `RUNTIME`

| Element | Type | Default | Notes |
| --- | --- | --- | --- |
| `alpha` | `boolean` | `false` | If true, generates an ARGB `ColorOption`; otherwise RGB. |

Only checked for `int` / `Integer` fields. If present, the field generates a `ColorOption` instead of an `IntegerOption`.

### @AutoKeybind

**Package:** `com.yoima.moddeck.api.autoconfig`
**Target:** `ElementType.FIELD`
**Retention:** `RUNTIME`

| Element | Type | Default | Notes |
| --- | --- | --- | --- |
| `keyboard` | `boolean` | `true` | Allow keyboard inputs. |
| `mouse` | `boolean` | `false` | Allow mouse inputs. |
| `modifiers` | `boolean` | `false` | Allow modifier chords. |
| `unbound` | `boolean` | `true` | Allow unbound state. |

Only checked for `String` fields. If present, the field generates a `KeybindOption` instead of a `StringOption`.
