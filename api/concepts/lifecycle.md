---
title: Lifecycle: Draft, Value, Save, and Callbacks
summary: State machine for option values, when callbacks fire, and how save commits drafts.
audience:
  - mod-developer
  - coding-agent
environment:
  - common
related_packages:
  - com.yoima.moddeck.api.option
  - com.yoima.moddeck.api
last_verified_commit: "4790b77de8b5c728d89099e1ac7637c4b99f1b29"
---

# Lifecycle: Draft, Value, Save, and Callbacks

## The two-value model

Every `ConfigOption<T>` has two values:

- **`value()`** — The last loaded or successfully saved value. Runtime consumers (game logic) should read this value.
- **`draftValue()`** — The value currently being edited in the config screen. UI widgets read and write this value.

Both start as the default value. They diverge when the user edits an option and converge when a save succeeds.

## State transitions

```
                    register()
                        |
                        v
              +-------------------+
              |  loaded or default |  <-- value == draft
              +-------------------+
                        |
                   setDraftValue()
                        v
              +-------------------+
              |     dirty state    |  <-- value != draft
              +-------------------+
                   /          \
         discardChanges()    save()
                /                \
               v                  v
    +-------------------+   +-------------------+
    |  value unchanged   |   | value = draft     |
    |  draft = value     |   | onSaved fires     |
    +-------------------+   | onSave fires       |
                            +-------------------+
```

## Detailed operations

### `setDraftValue(T value)`

1. Clears `validationError`.
2. Runs `validate(value)` (option-specific range/format check). If `validate` throws `IllegalArgumentException`, the exception propagates and `validationError` stays cleared. The draft is not changed.
3. Runs `ConfigValidator.validate(value)`.
4. If the `ConfigValidator` returns an invalid result: sets `validationError` to the result's error text and throws `IllegalArgumentException`. Draft is not changed.
5. If validation passes and the new value differs from current draft: sets `draftValue`, invokes `onChanged` consumer.
6. If `onChanged` throws: logged at `SEVERE`, swallowed. Draft is still changed.

### `setValue(T value)`

1. Same validation as `setDraftValue` (option-specific `validate()` first, then `ConfigValidator`).
2. Sets both `value` and `draftValue` to the validated value.
3. Clears `validationError`.
4. Does not invoke `onChanged` or `onSaved`.

This is a programmatic replacement of both values. It bypasses the draft/save flow.

### `trySetDraftValue(T value)` / `trySetValue(T value)`

Same as above but returns `boolean` instead of throwing. On any caught `RuntimeException`, sets `validationError` to a generic translatable error (`moddeck.validation.invalid`) if `validationError` is still null, and returns `false`.

Note the distinction in `validationError` state after a failed call:
- If the option-specific `validate()` throws: it does not set a specific error, so the `try*` catch installs the generic `moddeck.validation.invalid` error.
- If the `ConfigValidator` returned an invalid result during `validateValue`: `validationError` was already set to that result's error text before `IllegalArgumentException` was thrown, so it stays set after the `try*` catch block.
- The `try*` catch only assigns the generic `moddeck.validation.invalid` error when `validationError` is still null.

### `loadEncodedValue(Object encodedValue)`

1. Decodes the encoded value via `decode(Object)`.
2. Runs option-specific `validate()`. If it throws `IllegalArgumentException`, the exception propagates and `validationError` stays null.
3. Runs the `ConfigValidator`. If the result is invalid, throws `IllegalArgumentException`. `validationError` is not set by this method (it is a silent load).
4. Sets both `value` and `draftValue` to the decoded value.
5. Does not invoke any callbacks.

Used by storage backends during load. Silent — no callbacks fire and `validationError` is not set on failure (the caller is expected to handle or log the exception).

### `notifySaved()`

1. Sets `value = draftValue`.
2. Invokes `onSaved` consumer with the new value.
3. If `onSaved` throws: logged at `SEVERE`, swallowed.

Called by `ConfigDefinition.notifySaved()`, which is called by `ConfigScreenApi.save()` after persistence succeeds.

### `reset()`

Calls `setDraftValue(defaultValue())`. This is a draft change — it triggers `onChanged` if the default differs from the current draft. It does not save.

### `discardChanges()`

Sets `draftValue = value` and clears `validationError`. Does not trigger any callbacks. The option returns to its last loaded or saved state.

## Definition-level operations

### `ConfigDefinition.isDirty()`

Returns true if any option in any category (including nested subcategory children) is dirty. Non-persistent options (`ButtonOption`, `DescriptionOption`) always return `isDirty() == false`.

### `ConfigDefinition.isValid()`

Returns true if no option has a `validationError`. Flattens all categories and subcategories.

### `ConfigDefinition.reset()`

Calls `reset()` on every option. This sets all drafts to defaults. Does not save.

### `ConfigDefinition.discardChanges()`

Calls `discardChanges()` on every option. Restores all drafts to their last saved or loaded values.

### `ConfigDefinition.notifySaved()`

Calls `notifySaved()` on every option, then invokes the definition's `onSave` callback. If the callback throws, it is logged at `SEVERE` and swallowed.

This method is package-private — it is called only by `ConfigScreenApi.save()`.

### `ConfigDefinition.applyPreset(String presetId)`

1. Looks up the preset by ID. Throws if not found.
2. For each entry, resolves the option by category and option ID. Throws if not found.
3. Checks that the option is persistent. Throws if not.
4. Validates the value type compatibility and range (via `validateCandidate`).
5. If all entries pass: applies each via `setDraftValue`, triggering `onChanged` callbacks.
6. If any entry fails: throws `IllegalArgumentException` and no drafts change.

## Save flow (end to end)

1. User clicks Save in the config screen.
2. `ConfigScreenApi.save(definition)` is called.
3. `definition.isValid()` is checked. If false, `IllegalStateException` is thrown.
4. `storage.save(definition)` is called. This writes `encodeDraft()` values to disk.
5. If save throws `IOException`: propagates to caller. `notifySaved` is not called.
6. `definition.notifySaved()` is called:
   a. For each option: `value = draftValue`, `onSaved` consumer fires.
   b. Definition's `onSave` callback fires.

## Callback summary

| Callback | When it fires | Receives | Can throw? |
| --- | --- | --- | --- |
| `onChanged` | `setDraftValue` produces a different draft | New draft value | Yes — logged, swallowed |
| `onSaved` (option) | `notifySaved()` on the option | New committed value | Yes — logged, swallowed |
| `onSave` (definition) | `notifySaved()` on the definition, after all options | Nothing (Runnable) | Yes — logged, swallowed |

`setValue` and `loadEncodedValue` do not trigger any callbacks.

## Non-persistent options

`ButtonOption`, `DescriptionOption`, and `SubcategoryOption` (the expansion state) are non-persistent:

- `persistent()` returns `false`.
- `isDirty()` returns `false`.
- `encode()` / `encodeDraft()` are not called by storage.
- `reset()` is a no-op for `ButtonOption` and `DescriptionOption`.
- `SubcategoryOption.isDirty()` delegates to its children — it returns true if any child is dirty.
- `SubcategoryOption.notifySaved()` calls `notifySaved()` on all children.

## Related API

- [Saving and Loading](../guides/saving-and-loading.md)
- [Registering Options](../guides/registering-options.md)
- [Options Reference](../reference/options.md)
- [ConfigDefinition Reference](../reference/config-definition.md)
