# Cloth Config parity audit

This document records the public-feature audit used to design Mod Deck. It is a behavior and API
usability comparison, not a source port. Mod Deck contains no Cloth Config classes and has no Cloth
runtime or build dependency.

## Sources reviewed

- [Cloth Config official repository](https://github.com/shedaniel/cloth-config)
- [v15 ConfigEntryBuilder public API](https://github.com/shedaniel/cloth-config/blob/v15/common/src/main/java/me/shedaniel/clothconfig2/api/ConfigEntryBuilder.java)
- [v15 ConfigBuilder public API](https://github.com/shedaniel/cloth-config/blob/v15/common/src/main/java/me/shedaniel/clothconfig2/api/ConfigBuilder.java)
- [v15 entry implementations](https://github.com/shedaniel/cloth-config/tree/v15/common/src/main/java/me/shedaniel/clothconfig2/gui/entries)
- [Official Wiki, which links to the Cloth Config GitBook](https://github.com/shedaniel/cloth-config/wiki)

The audit is pinned to the official `v15` branch commit
`979d516e9743d10879053c5fe8ae00c2b4ffd708`. Future Cloth releases should be audited separately;
their implementation must never be copied into Mod Deck.

## Implemented parity

| Cloth-facing capability | Mod Deck design |
| --- | --- |
| Boolean toggle | `BooleanOption` and `SwitchWidget` |
| Int, long, float, double fields/ranges | Typed options with min/max/step and sliders with direct numeric input |
| String field | `StringOption`, maximum length, and custom validation |
| Enum selector | `EnumOption` with translated value labels |
| Generic selector/dropdown | `SelectorOption<T>` plus `ValueCodec<T>` and translated labels |
| RGB and alpha color | `ColorOption` with RGB/ARGB hex editor and preview |
| Key code | Server-safe `KeybindOption` string model and client key-capture widget |
| Int/long/float/double/string lists | Generic `ListOption<T>` and `ValueCodec<T>`; JSON arrays on disk |
| Subcategory | Recursive, collapsible `SubcategoryOption` with nested storage and search |
| Tooltip and value-dependent tooltip | Fixed or value-derived translated tooltip lines on every option |
| Default reset | Screen reset and recursive option/subcategory reset |
| Error supplier/validation | Typed `ConfigValidator<T>`, retained error state, row error, and save blocking |
| Save consumer | Per-option `onSaved` and definition-level `onSave`, invoked only after persistence succeeds |
| Change callback | Per-option `onChanged`, invoked only for accepted value changes |
| Requires restart | Persistent row metadata shown independently of the description |
| Search | Current-language title, category-entry ID, name, description, and nested-entry search |
| Dynamic categories | Arbitrary names/count/order/content; one-category and large-category layouts |
| Parent screen | Direct screen creation retains and returns to the supplied parent |
| Screen title/background | Translated title, transparent background, custom texture, accent metadata, optional save confirmation |
| Custom entry/widget | Public `ConfigOption<T>` extension point and client `OptionWidgetRegistry` factory registration |
| Stable direct navigation | `ConfigRoute` plus `ModDeckApi.create/openConfigScreen` by route or mod ID |

## Deliberate API differences

Mod Deck stores UI text as `ConfigText`, not pre-resolved strings. This is what lets a registering
mod own translations in its own resource namespace and lets language reloads update an open
screen. Storage-neutral option classes live in the common source set; Minecraft widgets and direct
screen opening live in the client source set. A dedicated server can therefore load registrations
and persistence without loading `Minecraft`, `Screen`, or `AbstractWidget`.

The default route is derived from the registered mod ID. Duplicate mod IDs and duplicate routes are
rejected atomically. JSON writes remain UTF-8 and use atomic replacement when the file system
supports it; one invalid persisted value does not discard other valid values.

## Remaining parity work

These Cloth features were identified but are not yet equivalent:

- Modifier combinations for keybinds; the current control captures one Minecraft key identifier.
- Per-element add/remove/reorder controls and per-element errors for lists; the current compact
  editor uses escaped semicolon-delimited input while preserving typed JSON arrays.
- Standalone text-description and empty spacer entries.
- Whole-screen read-only mode, after-init consumer, always-show-tabs, and smooth-scroll toggles.
- Cloth Auto Config's annotation/reflection serializers. This is a separate higher-level module,
  not part of the core declarative screen/storage API implemented here.
- A color palette/picker dialog; current RGB/ARGB entry is a validated hex field with preview.

These items should be added as Mod Deck-native APIs. They must not introduce a Cloth dependency or
reuse Cloth implementation code.
