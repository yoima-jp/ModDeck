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
        String key = InputConstants.getKey(event).getName();
        finish(option.allowsModifiers() ? withModifiers(key, event.modifiers()) : key);
        return true;
    }

    public boolean captureMouse(MouseButtonEvent event) {
        if (!listening) return false;
        if (option.allows(KeybindOption.InputType.MOUSE)) {
            String key = InputConstants.Type.MOUSE.getOrCreate(event.button()).getName();
            finish(option.allowsModifiers() ? withModifiers(key, event.modifiers()) : key);
        }
        // Consume unsupported mouse input while listening so it cannot activate another control.
        return true;
    }

    private void finish(String key) {
        if (option.trySetDraftValue(key)) {
            listening = false;
            setFocused(false);
            onChanged.run();
        }
    }

    @Override protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        DeckTheme.border(graphics, getX(), getY(), getWidth(), getHeight(), 5,
                listening ? DeckTheme.ACCENT : DeckTheme.DIVIDER, DeckTheme.FIELD);
        Component value = listening ? listeningPrompt()
                : option.isUnbound() ? Component.translatable("moddeck.keybind.unbound")
                : displayChord(option.draftValue());
        DeckTheme.centeredText(graphics, DeckFonts.ui(), value, getX() + getWidth() / 2, getY() + 10, DeckTheme.TEXT);
    }

    private Component listeningPrompt() {
        boolean keyboard = option.allows(KeybindOption.InputType.KEYBOARD);
        boolean mouse = option.allows(KeybindOption.InputType.MOUSE);
        if (keyboard && mouse) return Component.translatable("moddeck.keybind.press_input");
        if (mouse) return Component.translatable("moddeck.keybind.press_mouse");
        return Component.translatable("moddeck.keybind.press");
    }

    private static String withModifiers(String key, int modifiers) {
        StringBuilder chord = new StringBuilder();
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) chord.append("control+");
        if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) chord.append("shift+");
        if ((modifiers & GLFW.GLFW_MOD_ALT) != 0) chord.append("alt+");
        if ((modifiers & GLFW.GLFW_MOD_SUPER) != 0) chord.append("super+");
        return chord.append(key).toString();
    }

    private static Component displayChord(String chord) {
        String[] parts = chord.split("\\+");
        StringBuilder label = new StringBuilder();
        for (int index = 0; index < parts.length - 1; index++) {
            if (!label.isEmpty()) label.append(" + ");
            label.append(switch (parts[index]) {
                case "control" -> "Ctrl";
                case "shift" -> "Shift";
                case "alt" -> "Alt";
                case "super" -> "Super";
                default -> parts[index];
            });
        }
        if (!label.isEmpty()) label.append(" + ");
        label.append(InputConstants.getKey(KeybindOption.baseKey(chord)).getDisplayName().getString());
        return Component.literal(label.toString());
    }

    @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
}
