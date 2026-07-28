---
title: Options Reference
summary: ConfigOption base class and all built-in option types with exact signatures and behavior.
audience:
  - mod-developer
  - coding-agent
environment:
  - common
related_packages:
  - com.yoima.moddeck.api.option
  - com.yoima.moddeck.api.validation
last_verified_commit: "4790b77de8b5c728d89099e1ac7637c4b99f1b29"
---

# Options Reference

## ConfigOption<T>

**Package:** `com.yoima.moddeck.api.option`
**Environment:** common
**Source set:** `src/main/java`
**Kind:** abstract class

### Purpose

Base class for all typed configuration values. Holds the two-value model (value/draft), validation, callbacks, and UI-independent metadata.

### Construction

`ConfigOption` is abstract. Construct one of the built-in subclasses or your own subclass. The protected constructor requires: `id`, `displayName` (ConfigText or String), `description` (ConfigText or String), `defaultValue`, and `OptionPresentation`.

### Core value methods

| Method | Returns | Notes |
| --- | --- | --- |
| `id()` | `String` | Matches `[a-z0-9_.-]+`. |
| `value()` | `T` | Last loaded or saved value. |
| `draftValue()` | `T` | Current editing value. |
| `defaultValue()` | `T` | From fixed default or `defaultValueSupplier`. |
| `presentation()` | `OptionPresentation` | |
| `isDirty()` | `boolean` | `persistent() && !Objects.equals(draftValue, value)`. |
| `canResetDraft()` | `boolean` | `!Objects.equals(draftValue, defaultValue())`. |
| `persistent()` | `boolean` | Default `true`. Overridden by non-persistent types. |
| `validationError()` | `Optional<ConfigText>` | Set to the `ConfigValidator` error, or to the generic `moddeck.validation.invalid` error when a `try*` method catches a failure without a specific error. Direct option-specific `validate()` failures do not set it. |
| `formattedDraftValue()` | `ConfigText` | Via `valueFormatter`. |
| `isEnabled()` | `boolean` | Via `enableRequirement`. |
| `isDisplayed()` | `boolean` | Via `displayRequirement`. |
| `tooltips()` | `List<ConfigText>` | Via `tooltipFactory`. |
| `searchAliases()` | `List<String>` | Lowercase. |
| `isRestartRequired()` | `boolean` | |
| `editable()` | `boolean` | |

### Value mutation methods

| Method | Effect | Callbacks |
| --- | --- | --- |
| `setDraftValue(T)` | Validates and sets draft. | `onChanged` if changed. |
| `setValue(T)` | Validates and sets both value and draft. | None. |
| `trySetDraftValue(T)` | Same as `setDraftValue`, returns boolean. | `onChanged` if changed. |
| `trySetValue(T)` | Same as `setValue`, returns boolean. | None. |
| `tryDecodeAndSet(Object)` | Decodes, then `trySetValue`. | None. |
| `tryDecodeAndSetDraft(Object)` | Decodes, then `trySetDraftValue`. | `onChanged` if changed. |
| `loadEncodedValue(Object)` | Decodes, validates, sets both value and draft. | None. Used by storage. |
| `validateCandidate(T)` | Validates without changing state. | None. |
| `reset()` | `setDraftValue(defaultValue())`. | `onChanged` if changed. |
| `discardChanges()` | `draftValue = value`, clears error. | None. |
| `notifySaved()` | `value = draftValue`. | `onSaved`. |

### Metadata methods (return `this` for chaining)

| Method | Parameter | Notes |
| --- | --- | --- |
| `validateWith(ConfigValidator<T>)` | Validator | Applied immediately to current value and draft. |
| `defaultValueFrom(Supplier<T>)` | Dynamic default supplier | Applied immediately. |
| `onChanged(Consumer<T>)` | Change callback | Fires on `setDraftValue` change. |
| `onSaved(Consumer<T>)` | Save callback | Fires on `notifySaved`. |
| `tooltip(ConfigText...)` | Fixed tooltip lines | |
| `tooltip(Function<T, List<ConfigText>>)` | Dynamic tooltip factory | |
| `formatWith(Function<T, ConfigText>)` | Value formatter | |
| `enabledWhen(ConfigRequirement)` | Enable condition | |
| `displayedWhen(ConfigRequirement)` | Display condition | |
| `addSearchAliases(String...)` | Search aliases | Lowercased. |
| `requiresRestart()` | Marks restart needed | |
| `editable(boolean)` | Read-only toggle | |

### Abstract methods

| Method | Returns | Notes |
| --- | --- | --- |
| `decode(Object value)` | `T` | Converts storage-neutral value. Must throw on invalid. |
| `encode()` | `Object` | Converts `value()` for storage. Default: returns `value`. |
| `encodeDraft()` | `Object` | Converts `draftValue()` for storage. Default: returns `draftValue`. |

### Overridable methods

| Method | Default |
| --- | --- |
| `validate(T value)` | Returns value unchanged. |
| `isCompatibleValueType(Object candidate)` | `value.getClass().isInstance(candidate)`. |
| `persistent()` | `true`. |
| `isDirty()` | `persistent() && !Objects.equals(draftValue, value)`. |
| `reset()` | `setDraftValue(defaultValue())`. |
| `discardChanges()` | `draftValue = value; validationError = null;`. |
| `notifySaved()` | `value = draftValue; saveConsumer.accept(value);`. |

### Deprecated methods

| Method | Replacement |
| --- | --- |
| `displayName()` | `displayNameText()` |
| `description()` | `descriptionText()` |

### Failure behavior

| Condition | Exception | Propagates? |
| --- | --- | --- |
| `setDraftValue` with invalid value | `IllegalArgumentException` | Yes |
| `setValue` with invalid value | `IllegalArgumentException` | Yes |
| `loadEncodedValue` with invalid decoded value | `IllegalArgumentException` | Yes |
| `onChanged` callback throws | None — logged `SEVERE` | No |
| `onSaved` callback throws | None — logged `SEVERE` | No |
| `validateWith` rejects current value | `IllegalArgumentException` | Yes |

---

## BooleanOption

**Package:** `com.yoima.moddeck.api.option`
**Kind:** final class, extends `ConfigOption<Boolean>`
**Presentation:** `TOGGLE`

### Constructors

```java
BooleanOption(String id, String name, String description, boolean defaultValue)
BooleanOption(String id, ConfigText name, ConfigText description, boolean defaultValue)
```

### `decode`

Accepts `Boolean` or `String` ("true"/"false", case-insensitive).

---

## IntegerOption

**Kind:** final class, extends `ConfigOption<Integer>`
**Presentation:** `SLIDER`

### Constructors

```java
IntegerOption(String id, String name, String description, int defaultValue, int minimum, int maximum, int step)
IntegerOption(String id, ConfigText name, ConfigText description, int defaultValue, int minimum, int maximum, int step)
```

### Validation

- `minimum > maximum`, `step <= 0`, or default out of range: `IllegalArgumentException` at construction.
- Values are snapped to the nearest step: `minimum + round((value - minimum) / step) * step`, clamped to `maximum`.

### `decode`

Accepts `Number` (`.intValue()`) or `String` (`Integer.parseInt`).

### Additional methods

`minimum()`, `maximum()`, `step()` — all return `int`.

---

## LongOption

**Kind:** final class, extends `ConfigOption<Long>`
**Presentation:** `SLIDER`

### Constructors

```java
LongOption(String id, ConfigText name, ConfigText description, long defaultValue, long minimum, long maximum, long step)
```

Same validation and snapping as `IntegerOption` but for `long`.

### `decode`

Accepts `Number` (`.longValue()`) or `String` (`Long.parseLong`).

---

## FloatOption

**Kind:** final class, extends `ConfigOption<Float>`
**Presentation:** `SLIDER`

### Constructors

```java
FloatOption(String id, ConfigText name, ConfigText description, float defaultValue, float minimum, float maximum, float step)
```

Rejects non-finite values. Snapping uses `BigDecimal` from `Float.toString` to avoid binary artifacts.

### `decode`

Accepts `Number` (`.floatValue()`) or `String` (`Float.parseFloat`).

---

## DoubleOption

**Kind:** final class, extends `ConfigOption<Double>`
**Presentation:** `SLIDER`

### Constructors

```java
DoubleOption(String id, String name, String description, double defaultValue, double minimum, double maximum, double step)
DoubleOption(String id, ConfigText name, ConfigText description, double defaultValue, double minimum, double maximum, double step)
```

Rejects non-finite values. Snapping uses `BigDecimal.valueOf` to keep human-readable decimals.

### `decode`

Accepts `Number` (`.doubleValue()`) or `String` (`Double.parseDouble`).

---

## StringOption

**Kind:** final class, extends `ConfigOption<String>`
**Presentation:** `TEXT_FIELD`

### Constructors

```java
StringOption(String id, String name, String description, String defaultValue, int maximumLength)
StringOption(String id, ConfigText name, ConfigText description, String defaultValue, int maximumLength)
```

`maximumLength` must be >= 1 and default must not exceed it.

### `decode`

Accepts `String`. Validates length against `maximumLength`.

### Additional methods

`maximumLength()` — returns `int`.

---

## EnumOption<E extends Enum<E>>

**Kind:** final class, extends `ConfigOption<E>`
**Presentation:** `SELECTOR`

### Constructors

```java
EnumOption(String id, String name, String description, E defaultValue, Class<E> enumType)
EnumOption(String id, ConfigText name, ConfigText description, E defaultValue, Class<E> enumType)
```

### `encode` / `encodeDraft`

Returns `value().name()` / `draftValue().name()` (string).

### `decode`

Accepts `String`, calls `Enum.valueOf(enumType, text)`.

### `isCompatibleValueType`

Returns `enumType.isInstance(candidate)`.

### Additional methods

| Method | Returns | Notes |
| --- | --- | --- |
| `enumType()` | `Class<E>` | |
| `values()` | `List<E>` | All enum constants. |
| `label(E value)` | `ConfigText` | Via `labelFactory`. |
| `labels(Function<E, ConfigText>)` | `EnumOption<E>` | Sets the label factory. |
| `next()` | `E` | Cycles to next value, calls `setValue`. |

---

## ColorOption

**Kind:** final class, extends `ConfigOption<Integer>`
**Presentation:** `COLOR`

### Constructors

```java
ColorOption(String id, ConfigText name, ConfigText description, int defaultValue, boolean alpha)
```

When `alpha` is false, the default is masked to `0xFFFFFF` (RGB only).

### `decode`

Accepts `Number` (`.intValue()`) or `String` (hex with or without `#` prefix, 6 digits for RGB, 8 for ARGB).

### Additional methods

| Method | Returns | Notes |
| --- | --- | --- |
| `alpha()` | `boolean` | |
| `hexValue()` | `String` | `"%06X"` or `"%08X"`. |
| `draftHexValue()` | `String` | Same format for draft. |

---

## KeybindOption

**Kind:** final class, extends `ConfigOption<String>`
**Presentation:** `KEYBIND`

### Constants

`UNBOUND_KEY = "key.keyboard.unknown"`

### Nested types

`enum InputType { KEYBOARD, MOUSE }`

### Constructors

```java
KeybindOption(String id, ConfigText name, ConfigText description, String defaultKey)
KeybindOption(String id, ConfigText name, ConfigText description, String defaultKey,
              Set<InputType> allowedInputs, boolean allowUnbound)
```

The default constructor uses `Set.of(KEYBOARD)` and `allowUnbound = true`.

### Key format

Keys must match: `(?:(?:control|shift|alt|super)\+)*key\.(keyboard|mouse)\.[a-z0-9_.-]+`

### `decode`

Accepts `String`, validates against allowed inputs, unbound, and modifiers.

### Additional methods

| Method | Returns | Notes |
| --- | --- | --- |
| `allowedInputs()` | `Set<InputType>` | |
| `allows(InputType)` | `boolean` | |
| `allowsUnbound()` | `boolean` | |
| `allowsModifiers()` | `boolean` | |
| `isUnbound()` | `boolean` | Draft equals `UNBOUND_KEY`. |
| `allowModifiers(boolean)` | `KeybindOption` | Allows chord modifiers. Throws if disabling while current key has modifiers. |
| `allowedInputs(InputType first, InputType... remaining)` | `KeybindOption` | Replaces allowed set. Throws if current key type would be excluded. |
| `allowUnbound(boolean)` | `KeybindOption` | Throws if disabling while current key is unbound. |

### Static methods

| Method | Returns | Notes |
| --- | --- | --- |
| `baseKey(String chord)` | `String` | Strips modifier prefix. |
| `hasModifiers(String chord)` | `boolean` | True if chord contains `+`. |

---

## ListOption<T>

**Kind:** final class, extends `ConfigOption<List<T>>`
**Presentation:** `LIST`

### Constructors

```java
ListOption(String id, ConfigText name, ConfigText description, List<T> defaultValue,
           ValueCodec<T> elementCodec, int minimumSize, int maximumSize)
```

### Validation

- `minimumSize < 0`, `maximumSize < minimumSize`, or default size out of range: `IllegalArgumentException`.
- Each element is validated via `elementValidator`.

### `decode`

Accepts `List<?>`. Each element must be a `String` decoded by `elementCodec`.

### `encode` / `encodeDraft`

Returns `List<String>` of encoded elements.

### `isCompatibleValueType`

Returns `candidate instanceof List<?>`.

### Additional methods

| Method | Returns | Notes |
| --- | --- | --- |
| `minimumSize()` / `maximumSize()` | `int` | |
| `encodeElement(T)` | `String` | |
| `decodeElement(String)` | `T` | |
| `validateElement(T)` | `ValidationResult` | |
| `newElementText()` | `String` | Blank if no supplier. |
| `newElement()` | `T` | From supplier or `codec.decode("")`. |
| `insertionAllowed()` / `deletionAllowed()` / `reorderingAllowed()` | `boolean` | |
| `newElementFrom(Supplier<T>)` | `ListOption<T>` | |
| `validateElementsWith(ConfigValidator<T>)` | `ListOption<T>` | |
| `allowInsertion(boolean)` / `allowDeletion(boolean)` / `allowReordering(boolean)` | `ListOption<T>` | |

---

## SelectorOption<T>

**Kind:** final class, extends `ConfigOption<T>`
**Presentation:** `DROPDOWN`

### Constructors

```java
SelectorOption(String id, ConfigText name, ConfigText description, T defaultValue,
               List<T> choices, ValueCodec<T> codec, Function<T, ConfigText> labelFactory)
```

Choices must be non-empty and contain the default.

### `decode`

Accepts `String`, calls `codec.decode(text)`, validates against `choices`.

### `encode` / `encodeDraft`

Returns `codec.encode(value())` / `codec.encode(draftValue())`.

### `isCompatibleValueType`

Returns true if any choice's class `isInstance(candidate)`.

### Additional methods

| Method | Returns |
| --- | --- |
| `choices()` | `List<T>` |
| `label(T value)` | `ConfigText` |
| `encode(T value)` | `String` |

---

## SubcategoryOption

**Kind:** final class, extends `ConfigOption<Boolean>`
**Presentation:** `SUBCATEGORY`
**Persistent:** `false` (expansion state only; children are persistent individually)

### Construction

Via `SubcategoryOption.builder(String id, ConfigText name)`.

### Methods

| Method | Returns | Notes |
| --- | --- | --- |
| `children()` | `List<ConfigOption<?>>` | Immutable copy of the child options. Used by `ConfigDefinition` flattening, preset validation, and client rendering. |

### Builder methods

| Method | Notes |
| --- | --- |
| `description(ConfigText)` | |
| `initiallyExpanded(boolean)` | Default `true`. |
| `add(ConfigOption<?>)` | Duplicate child IDs throw. |
| `build()` | Requires at least one child. |

### Behavior

- `isDirty()` delegates to children.
- `reset()` calls `reset()` on all children.
- `discardChanges()` calls `discardChanges()` on self and all children.
- `notifySaved()` calls `notifySaved()` on all children.
- `persistent()` returns `false`.
- `decode(Object)` accepts `Boolean` for expansion state.

---

## DescriptionOption

**Kind:** final class, extends `ConfigOption<String>`
**Presentation:** `DESCRIPTION`
**Persistent:** `false`

### Constructors

```java
DescriptionOption(String id, ConfigText text)
```

Sets `editable(false)`. `decode` returns `""`. `isDirty` returns `false`. `reset` is a no-op.

---

## ButtonOption

**Kind:** final class, extends `ConfigOption<Boolean>`
**Presentation:** `BUTTON`
**Persistent:** `false`

### Constructors

```java
ButtonOption(String id, ConfigText name, ConfigText description, ConfigText buttonText, Runnable action)
```

### Additional methods

| Method | Returns | Notes |
| --- | --- | --- |
| `buttonText()` | `ConfigText` | |
| `runAction()` | `boolean` | Runs the action. Returns `true` on success, `false` on exception. Exceptions logged at `SEVERE`. |

### Behavior

- `persistent()` returns `false`.
- `isDirty()` returns `false`.
- `reset()` is a no-op.
- `notifySaved()` is a no-op.
- `decode(Object)` returns `false`.

---

## ValueCodec<T>

**Package:** `com.yoima.moddeck.api.option`
**Kind:** interface

```java
public interface ValueCodec<T> {
    String encode(T value);
    T decode(String value);
}
```

Used by `ListOption<T>` and `SelectorOption<T>` to convert between typed values and storage strings.

---

## ConfigValidator<T>

**Package:** `com.yoima.moddeck.api.validation`
**Kind:** `@FunctionalInterface` interface

```java
@FunctionalInterface
public interface ConfigValidator<T> {
    ValidationResult validate(T value);
    static <T> ConfigValidator<T> acceptingAll() { return value -> ValidationResult.success(); }
}
```

---

## ValidationResult

**Package:** `com.yoima.moddeck.api.validation`
**Kind:** record `(boolean valid, Optional<ConfigText> error)`

| Method | Returns | Notes |
| --- | --- | --- |
| `success()` | `ValidationResult` | Valid, no error. |
| `invalid(ConfigText error)` | `ValidationResult` | Invalid with error text. |

A valid result cannot have an error. An invalid result must have an error. These invariants are enforced in the constructor.
