package com.yoima.moddeck.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.yoima.moddeck.ModDeck;
import com.yoima.moddeck.client.screen.ModListScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class ModDeckClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(ModDeck.MOD_ID, "settings"));
        KeyMapping open = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.moddeck.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, category));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (open.consumeClick()) {
                client.setScreenAndShow(new ModListScreen(client.gui.screen()));
            }
        });
    }
}
