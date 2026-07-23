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
        .title("Example Mod")
        .category("general", "General")
        .booleanOption("enabled", "Enable Mod", true)
        .integerOption("volume", "Volume", 50, 0, 100)
        .doubleOption("opacity", "Opacity", 0.8, 0, 1)
        .stringOption("player_name", "Player Name", "Player")
        .enumOption("display_mode", "Display Mode", DisplayMode.SIMPLE, DisplayMode.class)
        .build()
);
```

The supported MVP option types are Boolean, Integer, Double, String, and Enum. Numeric options
carry minimum, maximum, and step metadata. `OptionPresentation` remains UI-independent so future
versions can offer multiple widgets for the same value type.

Definitions may also provide detail-header text with `.description("...")`.

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
the Settings hub. The included Example Mod definition exercises all five MVP types.

## Interface

The hub uses a responsive virtual canvas so Minecraft's automatic GUI scale does not collapse the
desktop-style layout. Its native 26.2 GUI rendering includes:

- searchable installed-mod sidebar and persistent selected-mod details
- category tabs and a fixed action footer
- automatic, light, and dark themes with purple selection and focus accents
- dedicated switch, range slider/value badge, text field, and expandable enum selector
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
