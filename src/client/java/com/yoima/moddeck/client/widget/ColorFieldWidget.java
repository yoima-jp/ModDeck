package com.yoima.moddeck.client.widget;

import com.yoima.moddeck.api.option.ColorOption;
import com.yoima.moddeck.client.theme.DeckTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;

public final class ColorFieldWidget extends EditBox {
    private static final int TEXT_INSET = 37;
    private final ColorOption option;

    public ColorFieldWidget(Font font, int x, int y, int width, ColorOption option, Runnable onChanged) {
        super(font, x, y, width, 28, option.displayNameText().component());
        this.option = option;
        setBordered(false);
        setMaxLength(option.alpha() ? 9 : 7);
        setValue("#" + option.hexValue());
        setResponder(value -> {
            if (option.tryDecodeAndSet(value)) onChanged.run();
        });
        active = option.editable();
    }

    @Override public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int swatch = option.alpha() ? option.value() : 0xFF000000 | option.value();
        DeckTheme.roundedRect(graphics, getX(), getY() + 4, 20, 20, 4, swatch);
        DeckTheme.border(graphics, getX() + 26, getY(), getWidth() - 26, getHeight(), 5,
                isFocused() ? DeckTheme.ACCENT_DARK : DeckTheme.DIVIDER, DeckTheme.FIELD);
        graphics.pose().pushMatrix();
        graphics.pose().translate(TEXT_INSET, (getHeight() - 8) / 2.0f);
        super.extractWidgetRenderState(graphics, mouseX, mouseY, delta);
        graphics.pose().popMatrix();
    }

    @Override public void onClick(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        super.onClick(new net.minecraft.client.input.MouseButtonEvent(
                event.x() - TEXT_INSET, event.y(), event.buttonInfo()), doubleClick);
    }
}
