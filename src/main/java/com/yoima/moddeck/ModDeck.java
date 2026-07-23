package com.yoima.moddeck;

import com.yoima.moddeck.api.ConfigScreenApi;
import com.yoima.moddeck.storage.JsonConfigStorage;
import com.yoima.moddeck.internal.example.ExampleConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public final class ModDeck implements ModInitializer {
    public static final String MOD_ID = "moddeck";

    @Override public void onInitialize() {
        // Storage is installed before external entrypoints normally register, while useStorage also
        // loads definitions registered earlier by unusual entrypoint ordering.
        ConfigScreenApi.useStorage(new JsonConfigStorage(
                FabricLoader.getInstance().getConfigDir().resolve(MOD_ID)));
        ExampleConfig.register();
    }
}
