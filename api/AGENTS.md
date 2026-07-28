---
title: AI Agent Instructions
summary: Rules for AI agents that read or update the ModDeck API documentation.
audience:
  - coding-agent
last_verified_commit: "4790b77de8b5c728d89099e1ac7637c4b99f1b29"
---

# AI Agent Instructions

## Reading order

1. Read [README.md](README.md) to identify which guide or reference page matches the task.
2. Read the relevant [guide](guides/) for step-by-step instructions and complete code examples.
3. Read [concepts/lifecycle.md](concepts/lifecycle.md) and [concepts/client-common-boundary.md](concepts/client-common-boundary.md) before writing code that touches draft values, save callbacks, or client-only classes.
4. Check the [reference](reference/) for exact signatures, failure conditions, and return types.
5. Only consult the implementation source code when the documentation does not answer the question.

## Source of truth

- Exact class names, type parameters, method signatures, arguments, return values, and exceptions are defined by the implementation source code under `src/main/java` and `src/client/java`.
- Recommended usage flows and call ordering are defined by the guides.
- State semantics and lifecycle meaning are defined by the concept pages.
- When code and documentation conflict, do not guess. Report the contradiction and identify which file needs updating.

## Prohibited behavior

- Do not use APIs that do not appear in the documentation or the implementation source.
- Do not invent methods, overloads, or constructors that are not present in the code.
- Do not use deprecated APIs in new code. Deprecated methods are marked `@Deprecated` in the reference.
- Do not reference client-only classes (`ModDeckApi`, `KeybindOptions`, `OptionWidgetRegistry`, anything under `com.yoima.moddeck.client`) from common-side code.
- Do not treat sample values in examples as actual defaults. Defaults are stated in the reference or the implementation.
- Do not assume state-change or callback timing. Read the lifecycle concept page.
- Do not fabricate integration code with external libraries (Cloth Config, Architectury, etc.). ModDeck has no such dependencies.

## Documentation updates

When a public API changes, update the documentation as follows:

| Change type | Update target |
| --- | --- |
| New option type or new public method on `ConfigOption` | [reference/options.md](reference/options.md), [guides/registering-options.md](guides/registering-options.md) |
| Change to `ConfigDefinition.Builder` API | [reference/config-definition.md](reference/config-definition.md), [guides/getting-started.md](guides/getting-started.md), [guides/registering-options.md](guides/registering-options.md) |
| Change to storage or persistence | [reference/storage.md](reference/storage.md), [guides/saving-and-loading.md](guides/saving-and-loading.md), [concepts/lifecycle.md](concepts/lifecycle.md) |
| Change to AutoConfig annotations or `AutoConfig.register` | [reference/auto-config.md](reference/auto-config.md), [guides/auto-config.md](guides/auto-config.md) |
| Change to `ConfigPreset` or `applyPreset` | [reference/presets.md](reference/presets.md), [guides/presets.md](guides/presets.md) |
| Change to client screen API (`ModDeckApi`, `OptionWidgetRegistry`) | [reference/client-api.md](reference/client-api.md), [guides/client-screens.md](guides/client-screens.md), [guides/custom-options.md](guides/custom-options.md) |
| Change to draft/value lifecycle, callbacks, or validation | [concepts/lifecycle.md](concepts/lifecycle.md), relevant reference pages |
| Change to source set boundary | [concepts/client-common-boundary.md](concepts/client-common-boundary.md) |

After updating any page, set `last_verified_commit` to the HEAD commit SHA of the change.