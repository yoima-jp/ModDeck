---
title: Client Screens
summary: Open a ModDeck config screen from client code, Mod Menu, commands, or a custom button.
audience:
  - mod-developer
  - coding-agent
environment:
  - client
related_packages:
  - com.yoima.moddeck.api
last_verified_commit: "4790b77de8b5c728d89099e1ac7637c4b99f1b29"
---

# Client Screens

## Goal

Open a ModDeck config screen from client-side code, bypassing the default hub or integrating with Mod Menu, a custom settings button, or a client command.

## Use this when

- You want a "Settings" button in your mod's screen that opens the ModDeck config directly.
- You integrate with Mod Menu or another settings hub.
- You want a client command that opens the config screen.

## Do not use this when

- You are writing common-side code. `ModDeckApi` is in the client source set and cannot be referenced from `src/main/java`.
- You just want the default hub. Players can assign the optional **Open Mod Deck** shortcut under Controls > Mod Deck.

## Prerequisites

- A config definition registered via `ConfigScreenApi.register` (common side).
- Source set: **client** (`src/client/java`).
- Your client entrypoint has access to a `Screen` parent (or `null`).

## Complete example

```java
package com.example.mymod.client;

import com.yoima.moddeck.api.ModDeckApi;
import com.yoima.moddeck.api.ConfigScreenApi;
import com.yoima.moddeck.api.ConfigRoute;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public final class MyModClientSettings {
    /**
     * Opens the config screen for "mymod", preserving the parent screen so the user
     * returns to it after closing the config.
     */
    public static void openSettings(Screen parentScreen) {
        ModDeckApi.openConfigScreen("mymod", parentScreen);
    }

    /**
     * Creates a screen object without immediately setting it as the active screen.
     * Useful when the caller manages screen transitions itself.
     */
    public static Screen createSettings(Screen parentScreen) {
        return ModDeckApi.createConfigScreen("mymod", parentScreen);
    }

    /**
     * Opens via a ConfigRoute. The default route is moddeck:config/<modId>, so changing
     * the mod ID also changes the route. Use a custom route (set via the builder) if you
     * need an identifier that is independent of the mod ID.
     */
    public static void openViaRoute(Screen parentScreen) {
        ConfigRoute route = ConfigScreenApi.route("mymod");
        ModDeckApi.openConfigScreen(route, parentScreen);
    }
}
```

## How it works

`ModDeckApi` provides four methods:

| Method | Returns | Effect |
| --- | --- | --- |
| `createConfigScreen(String modId, Screen parent)` | `Screen` | Creates a `ModListScreen` for the given mod. Does not change the active screen. |
| `createConfigScreen(ConfigRoute route, Screen parent)` | `Screen` | Same, but resolves by route. |
| `openConfigScreen(String modId, Screen parent)` | `void` | Creates the screen and immediately sets it as active via `Minecraft.getInstance().setScreenAndShow(...)`. |
| `openConfigScreen(ConfigRoute route, Screen parent)` | `void` | Same, but resolves by route. |

### ConfigRoute

`ConfigRoute` is a record with `namespace` and `path` fields. Every registered definition gets a default route of `moddeck:config/<modId>`. Because the default route embeds the mod ID, changing the mod ID also changes the route. To decouple the route from the mod ID, set a custom route via `ConfigDefinition.Builder.route(ConfigRoute)` at build time. You can obtain a registered definition's route via:

```java
ConfigRoute route = ConfigScreenApi.route("mymod");
```

Or parse a route string:

```java
ConfigRoute route = ConfigRoute.parse("moddeck:config/mymod");
```

## Lifecycle and side effects

- `createConfigScreen` does not change any game state. It constructs a `ModListScreen` that will read option drafts when rendered.
- `openConfigScreen` calls `Minecraft.getInstance().setScreenAndShow(...)`, which changes the active screen.
- The parent screen is preserved so that pressing Escape or the back button returns to it.

## Failure behavior

| Condition | Exception | Propagates? | Notes |
| --- | --- | --- | --- |
| Unknown mod ID | `IllegalArgumentException` | Yes | "No Mod Deck config is registered for \<modId\>" |
| Unknown route | `IllegalArgumentException` | Yes | "No Mod Deck config is registered for route \<route\>" |
| `modId` or `route` is null | `NullPointerException` | Yes | |

## Common mistakes

- Calling `ModDeckApi` from common-side code. It is in the client source set and references `net.minecraft.client.Minecraft` and `net.minecraft.client.gui.screens.Screen`. Use it only from `src/client/java`.
- Passing `null` as the parent screen when you want the player to return to the game menu. Pass `Minecraft.getInstance().screen` instead, or use the hub's own navigation.
- Forgetting to register the definition before calling `createConfigScreen`. The call throws if the mod ID is not in the registry.

## Related API

- [Getting Started](getting-started.md) — registration.
- [Client/Common Boundary](../concepts/client-common-boundary.md) — why these classes are client-only.
- [Client API Reference](../reference/client-api.md)
