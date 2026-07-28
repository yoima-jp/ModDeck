---
title: Presets Reference
summary: ConfigPreset, Builder, and Entry detailed reference.
audience:
  - mod-developer
  - coding-agent
environment:
  - common
related_packages:
  - com.yoima.moddeck.api
last_verified_commit: "4790b77de8b5c728d89099e1ac7637c4b99f1b29"
---

# Presets Reference

## ConfigPreset

**Package:** `com.yoima.moddeck.api`
**Environment:** common
**Source set:** `src/main/java`
**Kind:** final class, immutable

### Purpose

Immutable snapshot of named configuration values that can be applied to a `ConfigDefinition` as a batch of draft changes. A preset does not save; it only updates draft values.

### Methods

| Method | Returns | Notes |
| --- | --- | --- |
| `id()` | `String` | Matches `[a-z0-9_.-]+`. |
| `displayText()` | `ConfigText` | Display name. |
| `description()` | `ConfigText` | Description (default: `ConfigText.empty()`). |
| `entries()` | `List<Entry>` | Immutable list. |

### Static factory methods

| Method | Notes |
| --- | --- |
| `builder(String id, ConfigText displayText)` | |
| `builder(String id, String displayText)` | Literal display text. |
| `builderKey(String id, String displayKey)` | Translatable display text. |

---

## ConfigPreset.Builder

**Kind:** final static nested class

### Methods

#### `Builder description(ConfigText description)`

Sets the description. Default: `ConfigText.empty()`.

#### `Builder set(String categoryId, String optionId, Object value)`

Adds an entry. Both IDs must match `[a-z0-9_.-]+`. `value` must not be null.

Throws `IllegalArgumentException` if the same `categoryId:optionId` pair is set twice.

#### `ConfigPreset build()`

Throws `IllegalStateException` if no entries were added.

---

## ConfigPreset.Entry

**Kind:** record `(String categoryId, String optionId, Object value)`

### Validation in constructor

- `categoryId` and `optionId` must match `[a-z0-9_.-]+`.
- `value` must not be null.

### Fields

| Field | Type |
| --- | --- |
| `categoryId` | `String` |
| `optionId` | `String` |
| `value` | `Object` |

The `value` is the typed Java object that `ConfigOption.setDraftValue` would accept — not the encoded/storage form. For example, for an `EnumOption`, pass the enum constant, not its name string.

---

## ConfigDefinition.applyPreset(String presetId)

### Behavior

1. Looks up the preset by ID. Throws `IllegalArgumentException` if not found.
2. For each `Entry`:
   a. Resolves the option via `definition.option(categoryId, optionId)`. Throws `IllegalArgumentException` if not found.
   b. Checks `option.persistent()`. Throws `IllegalArgumentException` if false.
   c. Validates value type compatibility via `option.isCompatibleValue(value)`.
   d. Validates the value via `option.validateCandidate(typedValue)`.
3. If all entries pass: applies each via `setDraftValue`, which triggers `onChanged` callbacks.
4. If any entry fails: throws `IllegalArgumentException` and no drafts change (batch validation before application).

### Failure conditions

| Condition | Exception | State changed? |
| --- | --- | --- |
| Unknown preset ID | `IllegalArgumentException` | No |
| Unknown category/option | `IllegalArgumentException` | No |
| Non-persistent option targeted | `IllegalArgumentException` | No |
| Incompatible value type | `IllegalArgumentException` | No |
| Value fails validation | `IllegalArgumentException` | No |
| `setDraftValue` throws | `IllegalArgumentException` (wrapped) | No — earlier entries are not applied |

### What applyPreset does NOT do

- Does not call `onSaved` callbacks.
- Does not call the definition's `onSave` callback.
- Does not persist values.
- Does not change `value()` — only `draftValue()`.
