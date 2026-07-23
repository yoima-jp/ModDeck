package com.yoima.moddeck.api;

import com.yoima.moddeck.client.screen.ModListScreen;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Client entry point for direct navigation from Mod Menu, commands, hubs, or a mod's own UI. */
public final class ModDeckApi {
    private ModDeckApi() {}

    public static Screen createConfigScreen(String modId, Screen parentScreen) {
        ConfigRegistry.get(Objects.requireNonNull(modId, "modId")).orElseThrow(() ->
                new IllegalArgumentException("No Mod Deck config is registered for " + modId));
        return new ModListScreen(parentScreen, modId);
    }

    public static Screen createConfigScreen(ConfigRoute route, Screen parentScreen) {
        ConfigDefinition definition = ConfigRegistry.get(Objects.requireNonNull(route, "route")).orElseThrow(() ->
                new IllegalArgumentException("No Mod Deck config is registered for route " + route));
        return new ModListScreen(parentScreen, definition.modId());
    }

    public static void openConfigScreen(String modId, Screen parentScreen) {
        Minecraft.getInstance().setScreenAndShow(createConfigScreen(modId, parentScreen));
    }

    public static void openConfigScreen(ConfigRoute route, Screen parentScreen) {
        Minecraft.getInstance().setScreenAndShow(createConfigScreen(route, parentScreen));
    }
}
