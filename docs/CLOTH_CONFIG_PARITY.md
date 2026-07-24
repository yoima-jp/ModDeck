# Cloth Config parity audit

This document records the public-feature audit used to design Mod Deck. It is a behavior and API
usability comparison, not a source port. Mod Deck contains no Cloth Config classes and has no Cloth
runtime or build dependency.

## Sources reviewed

- [Cloth Config official repository](https://github.com/shedaniel/cloth-config)
- [v26.2 ConfigEntryBuilder public API](https://github.com/shedaniel/cloth-config/blob/v26.2/common/src/main/java/me/shedaniel/clothconfig2/api/ConfigEntryBuilder.java)
- [v26.2 ConfigBuilder public API](https://github.com/shedaniel/cloth-config/blob/v26.2/common/src/main/java/me/shedaniel/clothconfig2/api/ConfigBuilder.java)
- [v26.2 entry implementations](https://github.com/shedaniel/cloth-config/tree/v26.2/common/src/main/java/me/shedaniel/clothconfig2/gui/entries)
- [v26.2 conditional Requirement API](https://github.com/shedaniel/cloth-config/blob/v26.2/common/src/main/java/me/shedaniel/clothconfig2/api/Requirement.java)
- [v26.2 Auto Config](https://github.com/shedaniel/cloth-config/tree/v26.2/common/src/main/java/me/shedaniel/autoconfig)
- [Official Wiki, which links to the Cloth Config GitBook](https://github.com/shedaniel/cloth-config/wiki)

The audit is pinned to the official `v26.2` branch commit
`bb92b64dc85a5f014b7f10e13f07c2e4baafdfcf`. Future Cloth releases should be audited separately;
their implementation must never be copied into Mod Deck.

## Implemented parity

| Cloth-facing capability | Mod Deck design |
| --- | --- |
| Boolean toggle | `BooleanOption` and `SwitchWidget` |
| Int, long, float, double fields/ranges | Typed options with min/max/step and sliders with direct numeric input |
| String field | `StringOption`, maximum length, and custom validation |
| Enum selector | `EnumOption` with translated value labels |
| Generic selector/dropdown | `SelectorOption<T>` plus `ValueCodec<T>` and translated labels |
| RGB and alpha color | `ColorOption` with an in-place hue-wheel/SV popup and RGB/ARGB controls |
| Key and modifier key code | Server-safe chord model, keyboard/mouse capture, and `KeyMapping` adapter |
| Int/long/float/double/string lists | Generic `ListOption<T>`, add/remove/reorder, size and cell validation, and JSON arrays |
| Subcategory | Recursive, collapsible `SubcategoryOption` with nested storage and search |
| Tooltip and value-dependent tooltip | Fixed or value-derived translated tooltip lines on every option |
| Default reset | Screen, recursive group, and compact per-entry reset; fixed or supplied defaults |
| Error supplier/validation | Typed `ConfigValidator<T>`, retained error state, row error, and save blocking |
| Save consumer | Per-option `onSaved` and definition-level `onSave`, invoked only after persistence succeeds |
| Change callback | Per-option `onChanged`, invoked only for accepted value changes |
| Requires restart | Persistent row metadata shown independently of the description |
| Requirement | Composable draft-value conditions for display and enabled state |
| Search | Current-language title/name/description, IDs, aliases, and nested-entry search |
| Dynamic categories | Arbitrary names/count/order/content; one-category and large-category layouts |
| Parent screen | Direct screen creation retains and returns to the supplied parent |
| Screen policy | Translated title, screen-wide/per-entry read-only state, accent metadata, optional save confirmation |
| Custom entry/widget | Public `ConfigOption<T>` extension point and client `OptionWidgetRegistry` factory registration |
| Stable direct navigation | `ConfigRoute` plus `ModDeckApi.create/openConfigScreen` by route or mod ID |
| Auto Config | `@ModDeckAutoConfig` POJO discovery, field annotations, generated entries, JSON, load/save listeners |

## Deliberate API differences

Mod Deck stores UI text as `ConfigText`, not pre-resolved strings. This is what lets a registering
mod own translations in its own resource namespace and lets language reloads update an open
screen. Storage-neutral option classes live in the common source set; Minecraft widgets and direct
screen opening live in the client source set. A dedicated server can therefore load registrations
and persistence without loading `Minecraft`, `Screen`, or `AbstractWidget`.

The default route is derived from the registered mod ID. Duplicate mod IDs and duplicate routes are
rejected atomically. JSON writes remain UTF-8 and use atomic replacement when the file system
supports it; one invalid persisted value does not discard other valid values.

## Adopted v26.2 parity work

The following v26.2 capabilities were adopted without replacing Mod Deck's visual identity:

- staged screen edits with save-time commit and discard rollback;
- value-based enable/display requirements and requirement composition;
- per-entry reset, dynamic defaults, value formatters, descriptions, and search aliases;
- list add/remove/reorder, per-cell validation, bounds, and custom element creation;
- modifier key combinations and direct Minecraft key-mapping adapters;
- a Mod Deck-styled RGB/ARGB color picker;
- annotation-driven config discovery, JSON serialization, and load/save events.

## Intentionally excluded

These Cloth features either replace Mod Deck's defining UI or target compatibility outside this
Fabric library's purpose. They are intentionally not parity requirements:

- category-specific or arbitrary screen backgrounds, fallback categories, and alternate globalized
  layouts (legacy background metadata is ignored by the renderer);
- replacing the Mod Deck tab/card/footer composition with Cloth's screen layout;
- legacy Jankson, TOML4J, and YAML serializers when UTF-8 JSON already provides the supported
  storage contract;
- Forge and NeoForge modules;
- deprecated error-processing switches and other compatibility-only APIs;
- styleless empty spacers that add no user-facing information;
- Jankson/TOML/YAML-specific annotations and serializers, and loader-specific Forge/NeoForge APIs;
- cosmetic switches for Cloth's own tab/list easing and after-init hooks tied to Cloth screen
  internals.

The excluded items are not planned parity work. Any future equivalent must preserve the Mod Deck
shell, remain loader-appropriate, introduce no Cloth dependency, and reuse no Cloth implementation
code.
