---
title: Conditional Entries
summary: Show, hide, enable, or disable options based on draft values of other options.
audience:
  - mod-developer
  - coding-agent
environment:
  - common
related_packages:
  - com.yoima.moddeck.api
last_verified_commit: "4790b77de8b5c728d89099e1ac7637c4b99f1b29"
---

# Conditional Entries

## Goal

Make options appear, disappear, become enabled, or become disabled depending on the current draft values of other options in the same definition.

## Use this when

- A setting only makes sense when another setting has a particular value.
- You want to hide advanced options behind a toggle.
- You want to disable (grey out) an option without hiding it.

## Do not use this when

- You need cross-definition dependencies. `ConfigRequirement` only sees options within the same `ConfigDefinition`.
- You want to change option values programmatically based on conditions. Use `onChanged` callbacks for that.

## Prerequisites

- Read [Registering Options](registering-options.md) first.
- Source set: **common** (`src/main/java`).

## Complete example

```java
package com.example.mymod;

import com.yoima.moddeck.api.ConfigDefinition;
import com.yoima.moddeck.api.ConfigRequirement;
import com.yoima.moddeck.api.ConfigScreenApi;
import com.yoima.moddeck.api.ConfigText;
import com.yoima.moddeck.api.option.BooleanOption;
import com.yoima.moddeck.api.option.IntegerOption;
import com.yoima.moddeck.api.option.StringOption;
import com.yoima.moddeck.api.option.SubcategoryOption;

public final class ConditionalConfig {
    public enum Mode { SIMPLE, DETAILED }

    public static void register() {
        BooleanOption advanced = new BooleanOption(
            "advanced",
            ConfigText.translatable("mymod.option.advanced"),
            ConfigText.translatable("mymod.option.advanced.desc"),
            false
        );

        IntegerOption count = new IntegerOption(
            "count",
            ConfigText.translatable("mymod.option.count"),
            ConfigText.translatable("mymod.option.count.desc"),
            4, 0, 20, 1
        );
        count.enabledWhen(ConfigRequirement.isTrue(advanced))
             .displayedWhen(ConfigRequirement.isTrue(advanced));

        StringOption label = new StringOption(
            "label",
            ConfigText.translatable("mymod.option.label"),
            ConfigText.translatable("mymod.option.label.desc"),
            "Trace", 64
        );
        label.displayedWhen(ConfigRequirement.isTrue(advanced));

        ConfigScreenApi.register(
            ConfigDefinition.builder("mymod")
                .categoryKey("general", "mymod.category.general")
                .addOption(advanced)
                .addOption(count)
                .addOption(label)
                .build()
        );
    }
}
```

## How it works

`ConfigRequirement` is a `@FunctionalInterface` with a `boolean test()` method. Conditions evaluate against `draftValue()`, not `value()`. This means dependent controls react while the user is editing, before saving.

Two methods on `ConfigOption<T>` accept requirements:

- `enabledWhen(ConfigRequirement)` — the widget is interactive when `test()` returns true; greyed out when false.
- `displayedWhen(ConfigRequirement)` — the row is visible when `test()` returns true; hidden when false.

### Built-in requirement factories

| Factory | Condition |
| --- | --- |
| `ConfigRequirement.isTrue(option)` | Boolean option's draft is `true` |
| `ConfigRequirement.isFalse(option)` | Boolean option's draft is `false` |
| `ConfigRequirement.isValue(option, first, ...remaining)` | Option's draft is one of the given values |
| `ConfigRequirement.matches(first, second)` | Two options have equal drafts |
| `ConfigRequirement.not(requirement)` | Logical negation |
| `ConfigRequirement.all(requirements...)` | All must be true (AND) |
| `ConfigRequirement.any(requirements...)` | At least one must be true (OR) |
| `ConfigRequirement.none(requirements...)` | None must be true (NOR) |
| `ConfigRequirement.one(requirements...)` | Exactly one must be true (XOR) |

### Custom requirements

Because `ConfigRequirement` is a functional interface, you can write any boolean expression:

```java
count.displayedWhen(() -> mode.draftValue() == Mode.DETAILED && advanced.draftValue());
```

## Lifecycle and side effects

- Requirements are evaluated at render time, not at build time. Changing a draft value through `setDraftValue` immediately affects all dependent options' visibility and enabled state.
- A hidden option's `draftValue` is not reset or cleared. If the option was dirty before being hidden, it remains dirty. Save will still persist it.
- A disabled option's widget cannot be interacted with, but the draft value is preserved.

## Failure behavior

| Condition | Exception | Propagates? | Notes |
| --- | --- | --- | --- |
| Requirement throws `RuntimeException` | None — propagates to caller | Depends on caller | Requirements are called from the render thread; an exception will crash the screen render. |

There is no built-in fault isolation for requirement evaluation. Keep requirements simple and side-effect free.

## Common mistakes

- Using `value()` instead of `draftValue()` in a custom lambda. Conditions must read `draftValue()` to react while editing.
- Expecting a hidden option to be excluded from save. Hidden options are still persistent and still saved.
- Creating circular dependencies (A depends on B, B depends on A). This does not cause an exception but produces confusing UX.

## Related API

- [Registering Options](registering-options.md)
- [Lifecycle](../concepts/lifecycle.md)
- [ConfigRequirement Reference](../reference/core-types.md)