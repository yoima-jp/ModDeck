package com.yoima.moddeck.client.widget;

import com.mojang.blaze3d.platform.InputConstants;
import com.yoima.moddeck.api.option.KeybindOption;
import com.yoima.moddeck.client.theme.DeckFonts;
import com.yoima.moddeck.client.theme.DeckTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class KeybindWidget extends AbstractWidget {
    private final KeybindOption option;
    private final Runnable onChanged;
    private boolean listening;

    public KeybindWidget(int x, int y, int width, KeybindOption option, Runnable onChanged) {
        super(x, y, width, 28, option.displayNameText().component());
        this.option = option;
        this.onChanged = onChanged;
        active = option.editable();
    }

    public boolean isListening() { return listening; }

    @Override public void onClick(MouseButtonEvent event, boolean doubleClick) {
        listening = true;
        setFocused(true);
    }

    @Override public boolean keyPressed(KeyEvent event) {
        return listening ? captureKey(event) : super.keyPressed(event);
    }

    public boolean captureKey(KeyEvent event) {
        if (!listening) return false;
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            finish(KeybindOption.UNBOUND_KEY);
            return true;
        }
        if (!option.allows(KeybindOption.InputType.KEYBOARD)) return true;
        finish(InputConstants.getKey(event).getName());
        return true;
    }

    public boolean captureMouse(MouseButtonEvent event) {
        if (!listening) return false;
        if (option.allows(KeybindOption.InputType.MOUSE)) {
            finish(InputConstants.Type.MOUSE.getOrCreate(event.button()).getName());
        }
        // Consume unsupported mouse input while listening so it cannot activate another control.
        return true;
    }

    private void finish(String key) {
        if (option.trySetValue(key)) {
            listening = false;
            setFocused(false);
            onChanged.run();
        }
    }

    @Override protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        DeckTheme.border(graphics, getX(), getY(), getWidth(), getHeight(), 5,
                listening ? DeckTheme.ACCENT : DeckTheme.DIVIDER, DeckTheme.FIELD);
        Component value = listening ? Component.translatable("moddeck.keybind.press_input")
                : option.isUnbound() ? Component.translatable("moddeck.keybind.unbound")
                : InputConstants.getKey(option.value()).getDisplayName();
        graphics.centeredText(DeckFonts.ui(), value, getX() + getWidth() / 2, getY() + 10, DeckTheme.TEXT);
    }

    @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
}
