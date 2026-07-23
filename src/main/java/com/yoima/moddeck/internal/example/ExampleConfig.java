package com.yoima.moddeck.internal.example;

import com.yoima.moddeck.api.ConfigDefinition;
import com.yoima.moddeck.api.ConfigScreenApi;
import com.yoima.moddeck.api.ConfigText;
import com.yoima.moddeck.api.option.*;
import java.util.List;
import java.util.Set;

/** Development definition exercising translated text and the public entry extension surface. */
public final class ExampleConfig {
    private static final ValueCodec<String> STRING_CODEC = new ValueCodec<>() {
        @Override public String encode(String value) { return value; }
        @Override public String decode(String value) { return value; }
    };

    private ExampleConfig() {}

    public enum DisplayMode { SIMPLE, DETAILED, COMPACT }

    public static void register() {
        EnumOption<DisplayMode> mode = new EnumOption<>("display_mode", key("option.mode"), key("option.mode.desc"),
                DisplayMode.DETAILED, DisplayMode.class).labels(value -> key("mode." + value.name().toLowerCase()));
        StringOption profile = new StringOption("profile", key("option.profile"), key("option.profile.desc"),
                "Player", 32);
        SubcategoryOption advanced = SubcategoryOption.builder("advanced_group", key("subcategory.advanced"))
                .initiallyExpanded(false)
                .add(new LongOption("cache_limit", key("option.cache"), key("option.cache.desc"),
                        4096L, 0L, 65536L, 256L).requiresRestart())
                .add(new ListOption<>("tags", key("option.tags"), key("option.tags.desc"),
                        List.of("example", "deck"), STRING_CODEC, 0, 16))
                .build();

        ConfigScreenApi.register(ConfigDefinition.builder("example_mod")
                .titleKey("example_mod.config.title")
                .descriptionKey("example_mod.config.description")
                .categoryKey("general", "example_mod.category.general")
                .booleanOptionKey("enabled", "example_mod.option.enabled", "example_mod.option.enabled.desc", true)
                .integerOption("volume", key("option.volume"), key("option.volume.desc"), 75, 0, 100, 1)
                .floatOption("scale", key("option.scale"), key("option.scale.desc"), 1.0f, 0.5f, 2.0f, 0.1f)
                .doubleOption("opacity", key("option.opacity"), key("option.opacity.desc"), 0.65, 0, 1, 0.05)
                .addOption(profile)
                .addOption(mode)
                .colorOption("accent", key("option.accent"), key("option.accent.desc"), 0x8B5CF6, false)
                .keybindOption("action_key", key("option.key"), key("option.key.desc"), "key.keyboard.g",
                        Set.of(KeybindOption.InputType.KEYBOARD, KeybindOption.InputType.MOUSE), true)
                .selectorOption("quality", key("option.quality"), key("option.quality.desc"), "balanced",
                        List.of("fast", "balanced", "quality"), STRING_CODEC,
                        value -> key("quality." + value))
                .addOption(advanced)
                .categoryKey("appearance", "example_mod.category.appearance")
                .colorOption("overlay", key("option.overlay"), key("option.overlay.desc"), 0xCC221144, true)
                .build());
    }

    private static ConfigText key(String suffix) { return ConfigText.translatable("example_mod." + suffix); }
}
