---
title: Storage Reference
summary: ConfigStorage interface and JsonConfigStorage implementation.
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

# Storage Reference

## ConfigStorage

**Package:** `com.yoima.moddeck.api.storage`
**Environment:** common
**Source set:** `src/main/java`
**Kind:** interface

### Purpose

Replaceable persistence backend for registered definitions.

### Methods

#### `void load(ConfigDefinition definition) throws IOException`

Loads persisted values into the definition's options. Implementations should call `option.loadEncodedValue(Object)` for each option whose value is found in the store.

`loadEncodedValue` decodes, validates, and sets both `value()` and `draftValue()` silently (no callbacks).

#### `void save(ConfigDefinition definition) throws IOException`

Persists the definition's draft values. Implementations should call `option.encodeDraft()` for each persistent option.

#### `default void reset(ConfigDefinition definition) throws IOException`

Default implementation: calls `definition.reset()` (which resets all drafts to defaults), then calls `save(definition)` directly — not `ConfigScreenApi.save`.

This bypasses the normal save flow. Specifically, the default `reset`:
- Writes default drafts to the backend (via `save`).
- Does **not** commit drafts to `value()` — `value()` stays at whatever it was before the call.
- Does **not** invoke option-level `onSaved` callbacks.
- Does **not** invoke the definition's `onSave` callback.

To commit defaults as the active value and fire all save callbacks, call `definition.reset()` followed by `ConfigScreenApi.save(definition)` instead of `storage.reset(definition)`.

Custom backends can override `reset` for a more efficient implementation.

---

## JsonConfigStorage

**Package:** `com.yoima.moddeck.storage`
**Environment:** common
**Source set:** `src/main/java`
**Kind:** final class, implements `ConfigStorage`

### Purpose

UTF-8 JSON persistence with atomic replacement and per-option fault isolation.

### Constructor

```java
JsonConfigStorage(Path directory)
```

The directory is resolved to an absolute, normalized path. Files are stored as `<directory>/<modId>.json`.

### File location

```text
config/moddeck/<registered_mod_id>.json
```

ModDeck installs `JsonConfigStorage` pointing at `FabricLoader.getInstance().getConfigDir().resolve("moddeck")` during `ModDeck.onInitialize()`.

### JSON structure

```json
{
  "category_id": {
    "option_id": <encoded_value>,
    "subcategory_id": {
      "child_option_id": <encoded_value>
    }
  }
}
```

- Top-level keys: category IDs.
- Second-level keys: option IDs (or subcategory IDs for nested options).
- Non-persistent options are not written.
- `EnumOption` values: enum name string.
- `ColorOption` values: integer.
- `KeybindOption` values: key identifier string.
- `ListOption` values: JSON array of strings.
- `SelectorOption` values: string (codec-encoded).

### Load behavior

1. If the file does not exist: returns silently. Defaults remain.
2. Parses the file as a JSON object. If parsing fails: throws `IOException`.
3. For each category, reads the matching JSON object.
4. For each option:
   - If the option is a `SubcategoryOption`: recursively loads children from the nested object.
   - If the option's key is missing or `null`: skips it.
   - Otherwise: converts the JSON element to a Java value (Boolean, Number, String, or List) and calls `option.loadEncodedValue(rawValue)`.
5. If `loadEncodedValue` throws (invalid value): logs `WARNING` and continues to the next option. Other options in the same file are still loaded.

### Save behavior

1. Creates the directory if it does not exist.
2. Builds a JSON object from all categories and options.
3. For each option:
   - `SubcategoryOption`: nests children as a JSON object.
   - Persistent option: calls `option.encodeDraft()` and serializes the result.
   - Non-persistent option: skipped.
4. Writes to a temporary file in the directory.
5. Atomically moves the temporary file to `<modId>.json` (with `REPLACE_EXISTING` and `ATOMIC_MOVE`).
6. If `AtomicMoveNotSupportedException`: falls back to non-atomic move.
7. Cleans up the temporary file in a `finally` block.

### Path safety

`fileFor(definition)` resolves `<directory>/<modId>.json` and normalizes it. If the normalized path's parent does not equal the directory (path traversal via `..` in modId), throws `IllegalArgumentException`.

### Failure behavior

| Condition | Exception | Propagates? | State changed? |
| --- | --- | --- | --- |
| File not found during load | None — returns | No | Defaults remain |
| Malformed JSON during load | `IOException` | Yes | Defaults remain |
| Invalid value for one option during load | None — logged `WARNING` | No | That option keeps default; others load |
| Directory creation fails during save | `IOException` | Yes | No |
| Write fails during save | `IOException` | Yes | No |
| Atomic move unsupported | Falls back to non-atomic | No | File is still written |
| Path traversal in modId | `IllegalArgumentException` | Yes | No |
