package com.yoima.moddeck.client.screen;

import com.yoima.moddeck.api.option.ColorOption;
import com.yoima.moddeck.client.theme.DeckFonts;
import com.yoima.moddeck.client.theme.DeckTheme;
import com.yoima.moddeck.client.widget.DeckButton;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Compact RGB/ARGB picker using the same card, field, and accent language as the hub. */
public final class ColorPickerScreen extends Screen {
    private final Screen parent;
    private final ColorOption option;
    private final Runnable onChanged;
    private final List<EditBox> channels = new ArrayList<>();
    private Component error = Component.empty();

    public ColorPickerScreen(Screen parent, ColorOption option, Runnable onChanged) {
        super(option.displayNameText().component());
        this.parent = parent;
        this.option = option;
        this.onChanged = onChanged;
    }

    @Override protected void init() {
        channels.clear();
        int panelWidth = Math.min(440, width - 40);
        int x = (width - panelWidth) / 2;
        int value = option.draftValue();
        int[] values = option.alpha()
                ? new int[]{value >>> 24 & 255, value >>> 16 & 255, value >>> 8 & 255, value & 255}
                : new int[]{value >>> 16 & 255, value >>> 8 & 255, value & 255};
        String[] names = option.alpha() ? new String[]{"A", "R", "G", "B"} : new String[]{"R", "G", "B"};
        for (int index = 0; index < values.length; index++) {
            EditBox field = new EditBox(font, x + 110, 100 + index * 40, panelWidth - 130, 28,
                    Component.literal(names[index]));
            field.setValue(Integer.toString(values[index]));
            field.setMaxLength(3);
            channels.add(addRenderableWidget(field));
        }
        int bottom = height - 55;
        addRenderableWidget(new DeckButton(x + panelWidth - 190, bottom, 82, 28,
                Component.translatable("moddeck.cancel"), DeckButton.Style.THEME, this::onClose));
        addRenderableWidget(new DeckButton(x + panelWidth - 100, bottom, 82, 28,
                Component.translatable("moddeck.apply"), DeckButton.Style.PRIMARY, this::apply));
    }

    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, DeckTheme.BACKGROUND);
        int panelWidth = Math.min(440, width - 40);
        DeckTheme.roundedRect(graphics, (width - panelWidth) / 2, 30, panelWidth, height - 60, 9, DeckTheme.PANEL);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        extractBackground(graphics, mouseX, mouseY, delta);
        int panelWidth = Math.min(440, width - 40);
        int x = (width - panelWidth) / 2;
        graphics.text(DeckFonts.ui(), getTitle(), x + 18, 50, DeckTheme.TEXT, true);
        int color = previewColor();
        DeckTheme.border(graphics, x + 18, 72, 70, 70, 7, DeckTheme.DIVIDER, color);
        String[] names = option.alpha() ? new String[]{"A", "R", "G", "B"} : new String[]{"R", "G", "B"};
        for (int index = 0; index < names.length; index++) {
            graphics.text(DeckFonts.ui(), names[index], x + 110, 88 + index * 40, DeckTheme.TEXT_SECONDARY, false);
        }
        if (!error.getString().isEmpty()) graphics.text(DeckFonts.ui(), error, x + 18, height - 45, 0xFFFF9E9E, false);
        for (var child : children()) if (child instanceof Renderable renderable) {
            renderable.extractRenderState(graphics, mouseX, mouseY, delta);
        }
    }

    @Override public void onClose() { minecraft.setScreenAndShow(parent); }

    private int previewColor() {
        try { return option.alpha() ? parseArgb() : 0xFF000000 | parseRgb(); }
        catch (RuntimeException ignored) { return option.alpha() ? option.draftValue() : 0xFF000000 | option.draftValue(); }
    }

    private void apply() {
        try {
            option.setDraftValue(option.alpha() ? parseArgb() : parseRgb());
            onChanged.run();
            minecraft.setScreenAndShow(parent);
        } catch (RuntimeException exception) {
            error = Component.translatable("moddeck.color.invalid");
        }
    }

    private int parseRgb() {
        int r = channel(0), g = channel(1), b = channel(2);
        return r << 16 | g << 8 | b;
    }

    private int parseArgb() {
        int a = channel(0), r = channel(1), g = channel(2), b = channel(3);
        return a << 24 | r << 16 | g << 8 | b;
    }

    private int channel(int index) {
        int value = Integer.parseInt(channels.get(index).getValue());
        if (value < 0 || value > 255) throw new IllegalArgumentException("channel");
        return value;
    }
}
