---
title: Custom Options
summary: Subclass ConfigOption<T> to create a custom option type and register its client widget.
audience:
  - mod-developer
  - coding-agent
environment:
  - common
  - client
related_packages:
  - com.yoima.moddeck.api.option
  - com.yoima.moddeck.client.widget
last_verified_commit: "4790b77de8b5c728d89099e1ac7637c4b99f1b29"
---

# Custom Options

## Goal

Create a custom option type by subclassing `ConfigOption<T>`, and register a client widget for it via `OptionWidgetRegistry`.

## Use this when

- None of the built-in option types fit your data model.
- You need a completely custom Minecraft widget for your option.

## Do not use this when

- A built-in option type with metadata configuration (validators, formatters, conditional display) is sufficient.
- You only need a different visual presentation of a standard type. Use `OptionPresentation` and the built-in types.

## Prerequisites

- Read [Registering Options](registering-options.md) first.
- Common-side code goes in `src/main/java`.
- Client-side widget registration goes in `src/client/java`.

## Complete example

### Value type (common source set)

`src/main/java/com/example/mymod/config/Vector2i.java`

```java
package com.example.mymod.config;

/** Immutable 2D integer vector with value equality, so ConfigOption.isDirty() works correctly. */
public record Vector2i(int x, int y) {
}
```

The value type must have value equality because `ConfigOption.isDirty()` compares draft and applied values with `Objects.equals`. A mutable array (`int[]`) uses reference equality, so re-entering the same coordinates through a new array would always look dirty, while mutating the array in place would bypass change detection entirely. A `record` (or any type overriding `equals`/`hashCode`) avoids both problems.

### Custom option class (common source set)

`src/main/java/com/example/mymod/config/Vector2iOption.java`

```java
package com.example.mymod.config;

import com.yoima.moddeck.api.ConfigText;
import com.yoima.moddeck.api.OptionPresentation;
import com.yoima.moddeck.api.option.ConfigOption;

/**
 * Stores a 2D integer vector. The encoded form is a string "x,y" for storage.
 */
public final class Vector2iOption extends ConfigOption<Vector2i> {
    public Vector2iOption(String id, ConfigText name, ConfigText description, Vector2i defaultValue) {
        super(id, name, description, defaultValue, OptionPresentation.CUSTOM);
    }

    @Override
    public Vector2i decode(Object value) {
        if (value instanceof String text) {
            String[] parts = text.split(",");
            if (parts.length == 2) {
                return new Vector2i(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
            }
        }
        throw new IllegalArgumentException("Expected \"x,y\" string for " + id());
    }

    @Override
    public Object encode() {
        return value().x() + "," + value().y();
    }

    @Override
    public Object encodeDraft() {
        return draftValue().x() + "," + draftValue().y();
    }

    @Override
    protected boolean isCompatibleValueType(Object candidate) {
        return candidate instanceof Vector2i;
    }
}
```

### Widget registration (client source set)

`src/client/java/com/example/mymod/client/WidgetRegistration.java`

```java
package com.example.mymod.client;

import com.example.mymod.config.Vector2i;
import com.example.mymod.config.Vector2iOption;
import com.yoima.moddeck.client.widget.OptionWidgetRegistry;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public final class WidgetRegistration {
    public static void register() {
        OptionWidgetRegistry.register(Vector2iOption.class,
            (Font font, int x, int y, int width, Vector2iOption option, Runnable onChanged, boolean opensUp) -> {
                EditBox box = new EditBox(font, x, y, width, 20, Component.literal(option.id()));
                Vector2i draft = option.draftValue();
                box.setValue(draft.x() + "," + draft.y());
                box.setResponder(text -> {
                    String[] parts = text.split(",");
                    if (parts.length == 2) {
                        try {
                            Vector2i val = new Vector2i(
                                Integer.parseInt(parts[0].trim()),
                                Integer.parseInt(parts[1].trim()));
                            option.setDraftValue(val);
                            onChanged.run();
                        } catch (IllegalArgumentException ignored) { }
                    }
                });
                return box;
            });
    }
}
```

### Definition registration (common source set)

`src/main/java/com/example/mymod/MyMod.java`

```java
package com.example.mymod;

import com.example.mymod.config.Vector2i;
import com.example.mymod.config.Vector2iOption;
import com.yoima.moddeck.api.ConfigDefinition;
import com.yoima.moddeck.api.ConfigScreenApi;
import com.yoima.moddeck.api.ConfigText;
import net.fabricmc.api.ModInitializer;

public final class MyMod implements ModInitializer {
    public static final String MOD_ID = "mymod";

    @Override
    public void onInitialize() {
        ConfigScreenApi.register(
            ConfigDefinition.builder("mymod")
                .titleKey("mymod.config.title")
                .categoryKey("general", "mymod.category.general")
                .addOption(new Vector2iOption(
                    "position",
                    ConfigText.translatable("mymod.option.position"),
                    ConfigText.translatable("mymod.option.position.desc"),
                    new Vector2i(0, 0)
                ))
                .build()
        );
    }

}
```

Register the definition on the common source set during `onInitialize`.

### Client entrypoint (client source set)

`src/client/java/com/example/mymod/client/MyModClient.java`

```java
package com.example.mymod.client;

import net.fabricmc.api.ClientModInitializer;

public final class MyModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        WidgetRegistration.register();
    }
}
```

Register the widget factory on the client source set during `onInitializeClient`, before the config screen is opened. The client source set can see common classes, but the common source set cannot see client classes — so `WidgetRegistration` and `MyModClient` must both live in `src/client/java`.

### Registration in the definition

Short snippet — see the complete `MyMod` class above for imports and context.

```java
ConfigScreenApi.register(
    ConfigDefinition.builder("mymod")
        .categoryKey("general", "mymod.category.general")
        .addOption(new Vector2iOption(
            "position",
            ConfigText.translatable("mymod.option.position"),
            ConfigText.translatable("mymod.option.position.desc"),
            new Vector2i(0, 0)
        ))
        .build()
);
```

## How it works

### Subclassing `ConfigOption<T>`

Your subclass must override at minimum:

- `decode(Object value)` — Converts a storage-neutral value (Boolean, Number, String, or List) into the typed value `T`. Must throw `IllegalArgumentException` for invalid input.
- `encode()` — Converts the current `value()` to a storage-neutral form. Called by storage backends during save.
- `encodeDraft()` — Converts the current `draftValue()` to a storage-neutral form. Called by `JsonConfigStorage` during save.

You should also override:

- `isCompatibleValueType(Object candidate)` — Returns true if the candidate object can be cast to `T`. Used by preset application. The default checks `value.getClass().isInstance(candidate)`, which may be too narrow for interface-typed or collection-typed options.

Optional overrides:

- `validate(T value)` — Additional validation beyond the `ConfigValidator`. Called by `validateValue` before the `ConfigValidator`.
- `persistent()` — Return `false` for non-persistent options (like `ButtonOption`, `DescriptionOption`). Default is `true`.
- `isDirty()` — Override if the default dirty check (`persistent() && !Objects.equals(draftValue, value)`) is not appropriate.
- `reset()` — Override to customize reset behavior.
- `discardChanges()` — Override to customize discard behavior.
- `notifySaved()` — Override to customize save commit behavior.

### Registering a widget

`OptionWidgetRegistry.register(Class<T extends ConfigOption<?>>, Factory<T>)` maps your option class to a widget factory. The factory receives:

| Parameter | Description |
| --- | --- |
| `Font font` | Minecraft font for text rendering. |
| `int x, int y, int width` | Position and width allocated for the widget. |
| `T option` | The option instance. Read `draftValue()` and call `setDraftValue()` to update. |
| `Runnable onChanged` | Call this after `setDraftValue` so the screen updates dirty state and dependent conditions. |
| `boolean opensUp` | True if the widget should open upward (e.g., dropdown near the bottom of the screen). |

The factory must return an `AbstractWidget` (a Minecraft GUI widget).

### `OptionWidgetRegistry.create(...)`

Called internally by the screen renderer. It looks up the factory by `option.getClass()`. If no factory is registered for the exact class, it throws `IllegalArgumentException`. Inheritance is not followed — you must register the exact class.

## Lifecycle and side effects

- Widget registration is global and mutable. Calling `register` with the same class replaces the previous factory.
- The factory is called every time the screen rebuilds its option rows (language change, search filter, category switch).
- Your widget should read `draftValue()` on construction and call `setDraftValue()` + `onChanged.run()` on user interaction.

## Failure behavior

| Condition | Exception | Propagates? | Notes |
| --- | --- | --- | --- |
| No widget factory registered for an option class | `IllegalArgumentException` | Yes | Thrown when the screen tries to render the option. |
| `decode` receives an incompatible type | `IllegalArgumentException` | Yes | During storage load. |
| `setDraftValue` fails validation | `IllegalArgumentException` | Yes | From the widget. |

## Common mistakes

- Registering the widget factory on the wrong class (e.g., `ConfigOption.class` instead of `Vector2iOption.class`). The registry matches by exact class.
- Forgetting to call `onChanged.run()` after `setDraftValue`. The screen will not update dirty state, conditional entries, or the unsaved-change warning.
- Returning `null` from `encode()` or `encodeDraft()`. `JsonConfigStorage` will serialize `null`, which will be skipped on load (treated as `JsonNull`).
- Not overriding `isCompatibleValueType` for collection or interface types. The default uses `value.getClass().isInstance(candidate)`, which rejects valid alternative implementations.

## Related API

- [Registering Options](registering-options.md)
- [Client/Common Boundary](../concepts/client-common-boundary.md)
- [Options Reference](../reference/options.md)
- [Client API Reference](../reference/client-api.md)
