---
title: Client/Common Boundary
summary: Which APIs are safe in common code and which require the client source set.
audience:
  - mod-developer
  - coding-agent
environment:
  - common
  - client
last_verified_commit: "4790b77de8b5c728d89099e1ac7637c4b99f1b29"
---

# Client/Common Boundary

## The split

ModDeck uses Fabric Loom's `splitEnvironmentSourceSets()`. The project has two Java source sets:

- **`src/main/java`** (common) — Runs on both dedicated servers and clients. No Minecraft client classes.
- **`src/client/java`** (client) — Runs only on clients. Can reference `net.minecraft.client.*` classes.

The `fabric.mod.json` declares `"environment": "*"`. The common entrypoint `ModDeck` runs everywhere. The client entrypoint `ModDeckClient` runs only on clients.

## Which API packages are where

| Package | Source set | Client-only types |
| --- | --- | --- |
| `com.yoima.moddeck.api` | main (common) | All types are common-safe except `ConfigText` which references `net.minecraft.network.chat.Component` |
| `com.yoima.moddeck.api.option.*` | main (common) | All option types are common-safe |
| `com.yoima.moddeck.api.autoconfig` | main (common) | All types are common-safe |
| `com.yoima.moddeck.api.storage` | main (common) | `ConfigStorage` is common-safe |
| `com.yoima.moddeck.api.validation` | main (common) | All types are common-safe |
| `com.yoima.moddeck.storage` | main (common) | `JsonConfigStorage` uses Gson, no client classes |
| `com.yoima.moddeck.api` (client) | client | `ModDeckApi`, `KeybindOptions` |
| `com.yoima.moddeck.client.widget` | client | `OptionWidgetRegistry` and all widget classes |
| `com.yoima.moddeck.client.screen` | client | `ModListScreen` and all screen classes |
| `com.yoima.moddeck.client.theme` | client | Theme and font classes |

## ConfigText and Component

`ConfigText` is in the common source set but its `component()` method returns `net.minecraft.network.chat.Component`. `Component` is available on both sides (it is a shared Minecraft class), so `ConfigText` is safe to use in common code. However, calling `component().getString()` to resolve text requires a live Minecraft instance for translation. In common code, prefer storing `ConfigText` and letting the client resolve it at render time.

## Client-only APIs

### `ModDeckApi`

In `src/client/java/com/yoima/moddeck/api/ModDeckApi.java`. References `net.minecraft.client.Minecraft`, `net.minecraft.client.gui.screens.Screen`, and `com.yoima.moddeck.client.screen.ModListScreen`. Use only from client code.

### `KeybindOptions`

In `src/client/java/com/yoima/moddeck/api/KeybindOptions.java`. References `com.mojang.blaze3d.platform.InputConstants`, `net.minecraft.client.KeyMapping`, and `net.minecraft.client.Minecraft`. Use only from client code.

### `OptionWidgetRegistry`

In `src/client/java/com/yoima/moddeck/client/widget/OptionWidgetRegistry.java`. References `net.minecraft.client.gui.Font` and `net.minecraft.client.gui.components.AbstractWidget`. Use only from client code.

## Rule of thumb

If the class imports anything from `net.minecraft.client.*` or `com.mojang.blaze3d.*`, it is client-only. Everything under `com.yoima.moddeck.api` in the main source set is common-safe.

## Common mistake: referencing client classes from common

This will not compile in the common source set:

```java
// src/main/java — WRONG
import com.yoima.moddeck.api.ModDeckApi; // This is the client-side ModDeckApi
```

The `ModDeckApi` in the client source set shadows any common-side class with the same name. There is no common-side `ModDeckApi`. If you need to open a screen, do it from your client entrypoint.

## Related API

- [Client Screens](../guides/client-screens.md)
- [Custom Options](../guides/custom-options.md)
- [Client API Reference](../reference/client-api.md)