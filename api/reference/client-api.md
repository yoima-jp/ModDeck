---
title: Client API Reference
summary: ModDeckApi, KeybindOptions, and OptionWidgetRegistry.
audience:
  - mod-developer
  - coding-agent
environment:
  - client
related_packages:
  - com.yoima.moddeck.api
  - com.yoima.moddeck.client.widget
last_verified_commit: "4790b77de8b5c728d89099e1ac7637c4b99f1b29"
---

# Client API Reference

## ModDeckApi

**Package:** `com.yoima.moddeck.api` (client source set)
**Environment:** client only
**Source set:** `src/client/java`
**Kind:** final class, static methods only

### Purpose

Client entry point for direct navigation from Mod Menu, commands, hubs, or a mod's own UI.

### Methods

#### `static Screen createConfigScreen(String modId, Screen parentScreen)`

Creates a `ModListScreen` for the given mod ID. Does not change the active screen.

Throws `IllegalArgumentException` if no definition is registered for the mod ID.
Throws `NullPointerException` if `modId` is null.

#### `static Screen createConfigScreen(ConfigRoute route, Screen parentScreen)`

Creates a `ModListScreen` by resolving the route.

Throws `IllegalArgumentException` if no definition is registered for the route.
Throws `NullPointerException` if `route` is null.

#### `static void openConfigScreen(String modId, Screen parentScreen)`

Creates the screen and immediately sets it as active via `Minecraft.getInstance().setScreenAndShow(...)`.

Same failure conditions as `createConfigScreen(String, Screen)`.

#### `static void openConfigScreen(ConfigRoute route, Screen parentScreen)`

Creates the screen by route and immediately sets it as active.

Same failure conditions as `createConfigScreen(ConfigRoute, Screen)`.

---

## KeybindOptions

**Package:** `com.yoima.moddeck.api` (client source set)
**Environment:** client only
**Source set:** `src/client/java`
**Kind:** final class, static methods only

### Purpose

Client adapters for keeping a Mod Deck keybind option and a vanilla `KeyMapping` synchronized.

### Methods

#### `static KeybindOption fromKeyMapping(String id, ConfigText name, ConfigText description, KeyMapping mapping, boolean allowMouse, boolean allowUnbound)`

Creates a `KeybindOption` from a vanilla `KeyMapping`. The option's default key is `mapping.saveString()`. An `onSaved` callback is set that:

1. Calls `mapping.setKey(InputConstants.getKey(KeybindOption.baseKey(key)))`.
2. Calls `KeyMapping.resetMapping()`.
3. Calls `Minecraft.getInstance().options.save()`.

Parameters:
- `allowMouse` — if true, allows both KEYBOARD and MOUSE inputs; if false, KEYBOARD only.
- `allowUnbound` — if true, allows the unbound state.

---

## OptionWidgetRegistry

**Package:** `com.yoima.moddeck.client.widget`
**Environment:** client only
**Source set:** `src/client/java`
**Kind:** final class, static methods only

### Purpose

Central renderer mapping that keeps API option types independent from Minecraft widgets. Maps option classes to widget factories.

### Nested types

#### `Factory<T extends ConfigOption<?>>`

```java
@FunctionalInterface
public interface Factory<T extends ConfigOption<?>> {
    AbstractWidget create(Font font, int x, int y, int width, T option, Runnable onChanged, boolean opensUp);
}
```

### Methods

#### `static AbstractWidget create(Font font, int x, int y, int width, ConfigOption<?> option, Runnable onChanged, boolean opensUp)`

Looks up the factory for `option.getClass()` and creates the widget.

Throws `IllegalArgumentException` if no factory is registered for the option's class. The lookup is by exact class — inheritance is not followed.

#### `static synchronized <T extends ConfigOption<?>> void register(Class<T> type, Factory<T> factory)`

Registers or replaces a widget factory for a custom option class.

Throws `NullPointerException` if `type` or `factory` is null.

### Built-in registrations

| Option class | Widget class |
| --- | --- |
| `BooleanOption` | `SwitchWidget` |
| `IntegerOption` | `SliderWidget` |
| `DoubleOption` | `SliderWidget` |
| `LongOption` | `SliderWidget` |
| `FloatOption` | `SliderWidget` |
| `StringOption` | `TextFieldWidget` |
| `EnumOption` | `EnumSelectorWidget` |
| `SelectorOption` | `SelectorWidget` |
| `ColorOption` | `ColorFieldWidget` |
| `KeybindOption` | `KeybindWidget` |
| `ListOption` | `ListFieldWidget` |
| `SubcategoryOption` | `SubcategoryWidget` |
| `ButtonOption` | `ButtonOptionWidget` |

`DescriptionOption` does not have a registered factory — it is rendered directly by the screen layout, not through `OptionWidgetRegistry.create`.

### Thread safety

`register` is `synchronized`. The internal map is a `HashMap`. Registration is expected to happen during client initialization, before any screen is rendered.

### Failure behavior

| Condition | Exception | Propagates? |
| --- | --- | --- |
| No factory for option class | `IllegalArgumentException` | Yes |
| `type` or `factory` is null | `NullPointerException` | Yes |
