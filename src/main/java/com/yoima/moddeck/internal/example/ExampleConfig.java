package com.yoima.moddeck.internal.example;

import com.yoima.moddeck.api.ConfigDefinition;
import com.yoima.moddeck.api.ConfigScreenApi;

/** Development definition exercising every MVP option type through the public API. */
public final class ExampleConfig {
    private ExampleConfig() {}

    public enum DisplayMode { SIMPLE, DETAILED, COMPACT }

    public static void register() {
        ConfigScreenApi.register(ConfigDefinition.builder("example_mod")
                .title("Example Mod")
                .description("これはExample Modの設定画面の例です。")
                .category("general", "一般設定")
                .booleanOption("enabled", "Enable Example", "Example Modを有効にします。", true)
                .integerOption("volume", "Volume", "サウンドの音量を設定します。", 75, 0, 100, 1)
                .doubleOption("opacity", "Opacity", "画面の不透明度を設定します。", 0.65, 0, 1, 0.05)
                .stringOption("player_name", "Player Name", "ゲーム内に表示されるプレイヤー名です。", "Player", 32)
                .enumOption("display_mode", "Display Mode", "情報の表示モードを選択します。",
                        DisplayMode.DETAILED, DisplayMode.class)
                .category("appearance", "表示設定")
                .category("advanced", "詳細設定")
                .category("keys", "キー設定")
                .build());
    }
}
