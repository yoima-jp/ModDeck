package com.yoima.moddeck.api;

import com.yoima.moddeck.api.option.KeybindOption;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.Set;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/** Client adapters for keeping a Mod Deck keybind option and a vanilla KeyMapping synchronized. */
public final class KeybindOptions {
    private KeybindOptions() {}

    public static KeybindOption fromKeyMapping(String id, ConfigText name, ConfigText description,
                                               KeyMapping mapping, boolean allowMouse, boolean allowUnbound) {
        Set<KeybindOption.InputType> inputs = allowMouse
                ? Set.of(KeybindOption.InputType.KEYBOARD, KeybindOption.InputType.MOUSE)
                : Set.of(KeybindOption.InputType.KEYBOARD);
        KeybindOption option = new KeybindOption(id, name, description, mapping.saveString(), inputs, allowUnbound);
        option.onSaved(key -> {
            mapping.setKey(InputConstants.getKey(KeybindOption.baseKey(key)));
            KeyMapping.resetMapping();
            Minecraft.getInstance().options.save();
        });
        return option;
    }
}
