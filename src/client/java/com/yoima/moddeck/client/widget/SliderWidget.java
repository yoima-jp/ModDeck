package com.yoima.moddeck.client.widget;

import com.yoima.moddeck.api.option.*;
import com.yoima.moddeck.client.theme.DeckTheme;
import com.yoima.moddeck.client.theme.DeckFonts;
import java.math.BigDecimal;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Purple accent slider with separate range labels and value badge. */
public final class SliderWidget extends AbstractSliderButton {
    private static final int VALUE_WIDTH = 46;
    private final ConfigOption<?> option;
    private final double minimum;
    private final double maximum;
    private final Runnable onChanged;
    private boolean editing;
    private boolean replaceOnType;
    private String editingBuffer = "";

    public SliderWidget(int x, int y, int width, IntegerOption option, Runnable onChanged) {
        this(x, y, width, option, option.minimum(), option.maximum(), option.value(), onChanged);
    }

    public SliderWidget(int x, int y, int width, DoubleOption option, Runnable onChanged) {
        this(x, y, width, option, option.minimum(), option.maximum(), option.value(), onChanged);
    }

    private SliderWidget(int x, int y, int width, ConfigOption<?> option, double minimum, double maximum,
                         double current, Runnable onChanged) {
        super(x, y, width, 30, Component.empty(), normalize(current, minimum, maximum));
        this.option = option;
        this.minimum = minimum;
        this.maximum = maximum;
        this.onChanged = onChanged;
        updateMessage();
    }

    @Override public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int trackX = getX();
        int trackWidth = trackWidth();
        int trackY = getY() + 7;
        DeckTheme.roundedRect(graphics, trackX, trackY, trackWidth, 4, 2, DeckTheme.FIELD);
        int knobOffset = (int) Math.round(value * Math.max(1, trackWidth - 10));
        int knobX = trackX + knobOffset;
        DeckTheme.roundedRect(graphics, trackX, trackY, Math.max(4, knobOffset + 5), 4, 2, DeckTheme.ACCENT);
        DeckTheme.roundedRect(graphics, knobX, trackY - 3, 10, 10, 5, DeckTheme.TEXT);
        DeckTheme.border(graphics, knobX - 1, trackY - 4, 12, 12, 6, DeckTheme.ACCENT_DARK, DeckTheme.TEXT);

        var font = DeckFonts.ui();
        graphics.text(font, format(minimum), trackX, getY() + 19, DeckTheme.TEXT_SECONDARY, false);
        String max = format(maximum);
        graphics.text(font, max, trackX + trackWidth - font.width(max), getY() + 19, DeckTheme.TEXT_SECONDARY, false);
        int valueX = getX() + getWidth() - VALUE_WIDTH;
        DeckTheme.border(graphics, valueX, getY(), VALUE_WIDTH, 23, 5,
                editing ? DeckTheme.ACCENT : DeckTheme.DIVIDER, DeckTheme.FIELD);
        Component displayed = editing ? Component.literal(editingBuffer + "_") : getMessage();
        graphics.centeredText(font, displayed, valueX + VALUE_WIDTH / 2, getY() + 8, DeckTheme.TEXT);
    }

    @Override public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (event.x() >= valueFieldX()) {
            editing = true;
            replaceOnType = true;
            editingBuffer = getMessage().getString();
            setFocused(true);
            return;
        }
        commitEditing();
        // AbstractSliderButton maps clicks over the full widget width. This widget reserves the
        // right side for numeric input, so its built-in mapping places the handle left of the
        // pointer. Keep the superclass drag lifecycle, then remap against the visible track.
        super.onClick(event, doubleClick);
        setValueFromTrack(event.x());
    }

    @Override protected void onDrag(MouseButtonEvent event, double dragX, double dragY) {
        setValueFromTrack(event.x());
    }

    @Override public boolean charTyped(CharacterEvent event) {
        if (!editing || !event.isAllowedChatCharacter()) return false;
        String character = event.codepointAsString();
        if (!character.matches("[0-9.\\-]") || editingBuffer.length() >= 12) return true;
        if (replaceOnType) {
            editingBuffer = character;
            replaceOnType = false;
        } else {
            editingBuffer += character;
        }
        return true;
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (!editing) return super.keyPressed(event);
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            commitEditing();
            setFocused(false);
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            editing = false;
            updateMessage();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_BACKSPACE) {
            if (replaceOnType) {
                editingBuffer = "";
                replaceOnType = false;
            } else if (!editingBuffer.isEmpty()) {
                editingBuffer = editingBuffer.substring(0, editingBuffer.length() - 1);
            }
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_A && (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0) {
            replaceOnType = true;
            return true;
        }
        return true;
    }

    @Override public void setFocused(boolean focused) {
        if (!focused) commitEditing();
        super.setFocused(focused);
    }

    @Override protected void updateMessage() {
        setMessage(Component.literal(format(option.value() instanceof Number number ? number.doubleValue() : 0)));
    }

    @Override protected void applyValue() {
        double raw = minimum + value * (maximum - minimum);
        if (option instanceof IntegerOption integer) {
            integer.setValue((int) Math.round(raw));
        } else if (option instanceof DoubleOption decimal) {
            decimal.setValue(raw);
        }
        updateMessage();
        onChanged.run();
    }

    private static double normalize(double value, double min, double max) {
        return max == min ? 0 : (value - min) / (max - min);
    }

    private String format(double number) {
        if (option instanceof IntegerOption) return Integer.toString((int) Math.round(number));
        return BigDecimal.valueOf(number).stripTrailingZeros().toPlainString();
    }

    private int valueFieldX() {
        return getX() + getWidth() - VALUE_WIDTH;
    }

    private int trackWidth() {
        return Math.max(24, getWidth() - VALUE_WIDTH - 10);
    }

    private void setValueFromTrack(double mouseX) {
        int usableWidth = Math.max(1, trackWidth() - 10);
        setValue((mouseX - getX() - 5) / usableWidth);
    }

    private void commitEditing() {
        if (!editing) return;
        try {
            double parsed = Double.parseDouble(editingBuffer);
            double clamped = Math.max(minimum, Math.min(maximum, parsed));
            if (option instanceof IntegerOption integer) {
                integer.setValue((int) Math.round(clamped));
            } else if (option instanceof DoubleOption decimal) {
                decimal.setValue(clamped);
            }
            value = normalize(((Number) option.value()).doubleValue(), minimum, maximum);
            updateMessage();
            onChanged.run();
        } catch (NumberFormatException ignored) {
            // Invalid transient input is discarded; the last valid option value remains active.
            updateMessage();
        }
        editing = false;
        replaceOnType = false;
    }
}
