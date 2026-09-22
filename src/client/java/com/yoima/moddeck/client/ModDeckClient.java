package com.yoima.moddeck.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.yoima.moddeck.ModDeck;
import com.yoima.moddeck.client.screen.ModListScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public final class ModDeckClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(ModDeck.MOD_ID, "settings"));
        // A library mod must not claim a gameplay key by default. Players who want a global hub
        // shortcut can assign one explicitly in Controls; integrations may open screens directly.
        KeyMapping open = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.moddeck.open", InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), category));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (open.consumeClick()) {
                client.setScreenAndShow(new ModListScreen(client.gui.screen()));
            }
        });
    }
}
