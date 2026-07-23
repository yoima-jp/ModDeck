package com.yoima.moddeck.client.widget;

import com.yoima.moddeck.api.option.StringOption;
import com.yoima.moddeck.client.theme.DeckTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public final class TextFieldWidget extends EditBox {
    private static final int TEXT_INSET = 11;

    public TextFieldWidget(Font font, int x, int y, int width, StringOption option, Runnable onChanged) {
        super(font, x, y, width, 28, Component.literal(option.displayName()));
        setBordered(false);
        setMaxLength(option.maximumLength());
        setValue(option.value());
        setTextColor(DeckTheme.TEXT);
        setTextColorUneditable(DeckTheme.TEXT_MUTED);
        setResponder(value -> {
            option.setValue(value);
            onChanged.run();
        });
    }

    @Override public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        DeckTheme.border(graphics, getX(), getY(), getWidth(), getHeight(), 5,
                isFocused() ? DeckTheme.ACCENT_DARK : DeckTheme.DIVIDER,
                isHoveredOrFocused() ? DeckTheme.FIELD_HOVER : DeckTheme.FIELD);
        // In 26.2 an unbordered EditBox pins textY to getY(). The visual border is custom, so
        // translate only the vanilla text/cursor layer to retain the theme and center the line.
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
