# Mod Deck

Mod Deck is a declarative configuration-screen library for Minecraft Java Edition 26.2 and Fabric.
Mods register typed options through the public API; Mod Deck generates a consistent screen and
persists values without requiring each mod to implement Minecraft widgets or file I/O.

## Requirements

- Minecraft Java Edition 26.2
- Fabric Loader 0.19.3 or newer
- Fabric API 0.155.2+26.2 or newer
- Java 25

This is a non-obfuscated Fabric 26.x project. It uses Mojang's official names and the
`net.fabricmc.fabric-loom` plugin; Yarn mappings and remap tasks are intentionally absent.

## Using the API

Add the published `com.yoima:moddeck` artifact and declare `moddeck` as a dependency in your
`fabric.mod.json`. Register a definition from your mod initializer:

```java
ConfigScreenApi.register(
    ConfigDefinition.builder("example_mod")
        .titleKey("example_mod.config.title")
        .descriptionKey("example_mod.config.description")
        .categoryKey("general", "example_mod.category.general")
        .booleanOptionKey("enabled", "example_mod.option.enabled",
            "example_mod.option.enabled.description", true)
        .integerOption("volume",
            ConfigText.translatable("example_mod.option.volume"),
            ConfigText.translatable("example_mod.option.volume.description"),
            50, 0, 100, 1)
        .build()
);
```

`ConfigText.literal(...)` accepts fixed text and `ConfigText.translatable(...)` accepts a Minecraft
translation key. Put translations under the registering mod's own
`assets/<mod_id>/lang/<language>.json`; Mod Deck resolves them at render time and rebuilds widgets
when Minecraft reloads the active language.

Built-in entries cover Boolean, Integer, Long, Float, Double, String, Enum, RGB/ARGB Color,
Keybind, generic List, generic Selector/Dropdown, Slider, and nested Subcategory values. Entries
also support translated/dynamic tooltips, default reset, validators, change callbacks, save
consumers, read-only state, and restart-required metadata. `SubcategoryOption` recursively applies
storage, reset, validation, search, and callbacks to its children.

Editing updates the option's in-memory value and invokes `onChanged` immediately; use that callback
only for deliberate live previews. Until Save succeeds, the footer shows an unsaved-change warning.
Save persists the values, clears the dirty baseline, and then invokes `onSaved` and the definition's
`onSave` callback. Returning a value to its last saved value clears the warning automatically.

Keybind entries can independently allow keyboard keys, mouse buttons, and an unbound state:

```java
builder.keybindOption("action", name, description, "key.keyboard.g",
    Set.of(KeybindOption.InputType.KEYBOARD, KeybindOption.InputType.MOUSE), true);
```

While capturing input, Escape selects the unbound state instead of closing the parent screen.

Categories are not predefined. Registration order is the default display order; the
`category(id, text, order)` overload supplies an explicit order. A single category uses no tab bar,
while large category sets use a scrollable tab window.

For a custom entry, subclass `ConfigOption<T>` and register its client widget with
`OptionWidgetRegistry.register(...)`. This keeps the storage model independent from Minecraft
client classes while allowing a completely custom `AbstractWidget`.

Duplicate mod IDs, category IDs, and option IDs are rejected instead of silently overwritten.

## Storage

`ConfigStorage` is a public backend interface. The built-in `JsonConfigStorage` writes UTF-8 JSON
files atomically to:

```text
config/moddeck/<registered_mod_id>.json
```

Invalid values in an otherwise readable file are logged and ignored per option, leaving the
default value active. Mods may install another backend with `ConfigScreenApi.useStorage(...)`.

## Opening the screen

Join a world and press `K` (rebindable under Controls > Mod Deck). Select a registered mod from
the Settings hub. The included Example Mod definition exercises the translated built-in entries.

Every definition receives `moddeck:config/<mod_id>` by default. Client integrations can bypass the
hub and preserve their parent screen:

```java
Screen screen = ModDeckApi.createConfigScreen("example_mod", parentScreen);
ModDeckApi.openConfigScreen(ConfigScreenApi.route("example_mod"), parentScreen);
```

This is suitable for a mod's own settings button, Mod Menu, another settings hub, or a client
command. Unknown mod IDs and routes fail immediately instead of opening an empty screen.

## Interface

The hub uses a responsive virtual canvas so Minecraft's automatic GUI scale does not collapse the
desktop-style layout. Its native 26.2 GUI rendering includes:

- searchable installed-mod sidebar and persistent selected-mod details
- fully mod-defined category tabs, nested subcategories, search, and a fixed action footer
- automatic, light, and dark themes with purple selection and focus accents
- dedicated switch, numeric slider/field, text/list/color/keybind controls, and selectors
- bundled Noto Sans JP UI typography and Lucide SVG-derived icons
- compact layout scaling without changing the player's global GUI-scale preference

Bundled font and icon licenses are documented in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)
and included in the built jar under `META-INF/licenses/`.

## Development

```powershell
.\gradlew.bat test build --no-daemon --stacktrace
.\gradlew.bat runClient --no-daemon
```

Public API lives under `com.yoima.moddeck.api`. Minecraft client classes are isolated in the
`src/client` source set so the common entrypoint remains dedicated-server safe.

The Cloth Config feature audit and current parity decisions are documented in
[docs/CLOTH_CONFIG_PARITY.md](docs/CLOTH_CONFIG_PARITY.md). Mod Deck does not depend on Cloth
Config, Architectury, Auto Config, or any Cloth implementation classes.
