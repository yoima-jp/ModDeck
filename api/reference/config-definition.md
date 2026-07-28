---
title: ConfigDefinition Reference
summary: ConfigDefinition, Builder, and ConfigCategory detailed reference.
audience:
  - mod-developer
  - coding-agent
environment:
  - common
related_packages:
  - com.yoima.moddeck.api
last_verified_commit: "4790b77de8b5c728d89099e1ac7637c4b99f1b29"
---

# ConfigDefinition Reference

## ConfigDefinition

**Package:** `com.yoima.moddeck.api`
**Environment:** common
**Source set:** `src/main/java`
**Kind:** final class

### Purpose

Immutable screen structure containing mutable typed option values. The structure (categories, options, presets, style) is fixed at build time. Option values (value/draft) are mutable.

### Identifier constraints

| Identifier | Pattern | Scope |
| --- | --- | --- |
| `modId` | `[a-z0-9_.-]+` | Unique across all registered definitions. |
| Category ID | `[a-z0-9_.-]+` | Unique within a definition. |
| Option ID | `[a-z0-9_.-]+` | Unique within a category, including nested subcategory children. |
| Preset ID | `[a-z0-9_.-]+` | Unique within a definition. |

### Methods

#### `static Builder builder(String modId)`

Creates a new builder. Throws `IllegalArgumentException` if `modId` is null or does not match `[a-z0-9_.-]+`.

#### `String modId()`

#### `ConfigRoute route()`

#### `ConfigText titleText()`

#### `ConfigText descriptionText()`

#### `List<ConfigCategory> categories()`

Returns categories sorted by their `order` value.

#### `ConfigScreenStyle style()`

#### `boolean isValid()`

Flattens all categories and nested subcategories. Returns `true` if no option has a `validationError`.

#### `boolean isDirty()`

Flattens all categories (top-level options only — `SubcategoryOption.isDirty()` delegates to children). Returns `true` if any option is dirty.

#### `Optional<ConfigOption<?>> option(String categoryId, String optionId)`

Searches the specified category, flattening subcategory children. Returns the option if found.

#### `void reset()`

Calls `reset()` on every option in every category. This is a draft-only operation — no save is triggered.

#### `void discardChanges()`

Calls `discardChanges()` on every option. Restores all drafts to their last saved or loaded values.

#### `List<ConfigPreset> presets()`

#### `Optional<ConfigPreset> preset(String id)`

#### `void applyPreset(String presetId)`

See [Presets Reference](presets.md).

#### `void notifySaved()` (package-private)

Called by `ConfigScreenApi.save` only. Commits all drafts to values and fires callbacks. Not part of the public API.

### Deprecated methods

| Method | Replacement |
| --- | --- |
| `title()` | `titleText()` |
| `description()` | `descriptionText()` |

---

## ConfigDefinition.Builder

**Kind:** final static nested class

### State

The builder maintains:
- `modId` (fixed at construction)
- `route` (default: `ConfigRoute.forMod(modId)`)
- `title` (default: `ConfigText.literal(modId)`)
- `description` (default: `ConfigText.empty()`)
- `LinkedHashMap<String, CategoryBuilder> categories` (insertion-ordered)
- `CategoryBuilder currentCategory` (null until `category(...)` is called)
- `style` (default: `ConfigScreenStyle.DEFAULT`)
- `saveCallback` (default: `() -> {}`)
- `editable` (default: `true`)
- `LinkedHashMap<String, ConfigPreset> presets`

### Category methods

| Method | Signature | Notes |
| --- | --- | --- |
| `category` | `(String id, String displayName)` | Literal, auto order. |
| `categoryKey` | `(String id, String translationKey)` | Translatable, auto order. |
| `category` | `(String id, ConfigText displayName)` | ConfigText, auto order. |
| `category` | `(String id, ConfigText displayName, int order)` | Explicit order. |

Category IDs must match `[a-z0-9_.-]+`. Duplicate category IDs throw `IllegalArgumentException`. The `order` value determines display order after build.

### Option methods

See [Options Reference](options.md) for the full list of option convenience methods and their signatures.

All option methods require a current category. Calling before `category(...)` throws `IllegalStateException("Call category() before adding options")`.

### Preset methods

#### `Builder preset(ConfigPreset preset)`

Adds a preset. Duplicate preset IDs throw `IllegalArgumentException`.

### Other methods

| Method | Notes |
| --- | --- |
| `route(ConfigRoute)` | Overrides default route. |
| `title(String)` / `titleKey(String, Object...)` / `title(ConfigText)` | |
| `description(String)` / `descriptionKey(String, Object...)` / `description(ConfigText)` | |
| `style(ConfigScreenStyle)` | |
| `onSave(Runnable)` | Definition-level save callback. |
| `editable(boolean)` | Makes all options read-only when false. |
| `addOption(ConfigOption<?>)` | Adds a pre-constructed option. Alias: `add(ConfigOption<?>)` (private). |

### `build()`

1. If `categories` is empty: throws `IllegalStateException("At least one category is required")`.
2. Sorts categories by `order`.
3. If `editable` is false: sets all options (including nested) to `editable(false)`.
4. Validates presets: every preset target must reference an existing, persistent option. Throws `IllegalArgumentException` for unknown categories, unknown options, or non-persistent targets.
5. Checks for duplicate option IDs within each category (including nested subcategory children). Throws `IllegalArgumentException`.
6. Creates and returns the `ConfigDefinition`.

### Preset validation at build vs. apply time

| Check | When | Exception |
| --- | --- | --- |
| Unknown category in preset target | `build()` | `IllegalArgumentException` |
| Unknown option in preset target | `build()` | `IllegalArgumentException` |
| Non-persistent option in preset target | `build()` | `IllegalArgumentException` |
| Duplicate preset ID | `builder.preset()` | `IllegalArgumentException` |
| Value type incompatible with option | `applyPreset()` | `IllegalArgumentException` |
| Value fails validation | `applyPreset()` | `IllegalArgumentException` |

---

## ConfigCategory

**Package:** `com.yoima.moddeck.api`
**Kind:** final class, immutable

### Construction

Package-private constructor. Created by `CategoryBuilder.build()` during `ConfigDefinition.Builder.build()`.

### Methods

| Method | Returns |
| --- | --- |
| `id()` | `String` |
| `displayNameText()` | `ConfigText` |
| `order()` | `int` |
| `options()` | `List<ConfigOption<?>>` |

### Deprecated

| Method | Replacement |
| --- | --- |
| `displayName()` | `displayNameText()` |
