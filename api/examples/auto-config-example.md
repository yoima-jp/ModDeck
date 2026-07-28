---
title: Complete Example: AutoConfig Registration
summary: A self-contained, compilable example using annotation-driven registration.
audience:
  - mod-developer
  - coding-agent
environment:
  - common
related_packages:
  - com.yoima.moddeck.api.autoconfig
last_verified_commit: "4790b77de8b5c728d89099e1ac7637c4b99f1b29"
---

# Complete Example: AutoConfig Registration

```java
package com.example.mymod;

import com.yoima.moddeck.api.autoconfig.AutoColor;
import com.yoima.moddeck.api.autoconfig.AutoConfig;
import com.yoima.moddeck.api.autoconfig.AutoConfigHolder;
import com.yoima.moddeck.api.autoconfig.AutoEntry;
import com.yoima.moddeck.api.autoconfig.AutoIgnore;
import com.yoima.moddeck.api.autoconfig.AutoKeybind;
import com.yoima.moddeck.api.autoconfig.AutoRange;
import com.yoima.moddeck.api.autoconfig.ModDeckAutoConfig;

import net.fabricmc.api.ModInitializer;

import java.util.List;

public final class MyMod implements ModInitializer {
    public static final String MOD_ID = "mymod";

    @ModDeckAutoConfig(
        modId = "mymod",
        titleKey = "mymod.config.title",
        descriptionKey = "mymod.config.description"
    )
    public static final class Config {
        // Category "general" (default), order 0
        @AutoEntry(nameKey = "mymod.option.enabled")
        boolean enabled = true;

        @AutoEntry(nameKey = "mymod.option.count", order = 1)
        @AutoRange(min = 0, max = 100, step = 1)
        int count = 50;

        @AutoEntry(nameKey = "mymod.option.mode", order = 2, requiresRestart = true)
        DisplayMode mode = DisplayMode.DETAILED;

        // Category "look", order 2
        @AutoEntry(nameKey = "mymod.option.color", category = "look",
                   categoryKey = "mymod.category.look", categoryOrder = 2, order = 0)
        @AutoColor(alpha = true)
        int color = 0xCC221144;

        @AutoEntry(nameKey = "mymod.option.scale", category = "look",
                   categoryKey = "mymod.category.look", categoryOrder = 2, order = 1)
        @AutoRange(min = 0.5, max = 2.0, step = 0.1)
        float scale = 1.0f;

        @AutoEntry(nameKey = "mymod.option.hotkey", category = "look",
                   categoryKey = "mymod.category.look", categoryOrder = 2, order = 2)
        @AutoKeybind(keyboard = true, mouse = true, modifiers = false, unbound = true)
        String hotkey = "key.keyboard.g";

        @AutoEntry(nameKey = "mymod.option.tags", category = "look",
                   categoryKey = "mymod.category.look", categoryOrder = 2, order = 3)
        List<String> tags = List.of("example");

        // Ignored — not part of the config screen
        @AutoIgnore
        String internalCache = "hidden";

        // Not annotated — also ignored
        private transient int lastSavedCount = -1;
    }

    public enum DisplayMode { SIMPLE, DETAILED, COMPACT }

    private static AutoConfigHolder<Config> holder;

    @Override
    public void onInitialize() {
        Config config = new Config();
        holder = AutoConfig.register(config);

        holder.onLoad(cfg -> {
                    // Called immediately with the current config (fields already synchronized).
                    // AutoConfigHolder does not re-invoke load listeners on later loads.
                    applyConfig(cfg);
                })
              .onSave(cfg -> {
                    // Called after each successful save.
                    applyConfig(cfg);
                });
    }

    public static Config config() {
        return holder.config();
    }

    private static void applyConfig(Config cfg) {
        // Apply config values to your mod's runtime state here.
        // Fields are already updated by AutoConfig before listeners fire.
    }
}
```

## What this example demonstrates

- `@ModDeckAutoConfig` on the config class with `modId`, `titleKey`, and `descriptionKey`.
- `@AutoEntry` on every field that should appear in the config screen.
- `@AutoRange` on numeric fields (`int count`, `float scale`).
- `@AutoColor(alpha = true)` on `int color` to generate an ARGB `ColorOption`.
- `@AutoKeybind` on `String hotkey` to generate a `KeybindOption`.
- `@AutoIgnore` on `internalCache` to explicitly exclude it.
- Category grouping via `category` and `categoryKey` attributes.
- `requiresRestart = true` on `mode`.
- `List<String> tags` generates a `ListOption<String>`.
- `onLoad` and `onSave` listeners on the returned `AutoConfigHolder`.
- The config POJO fields are synchronized automatically: after registration, fields reflect loaded values; after save, fields reflect saved values.
