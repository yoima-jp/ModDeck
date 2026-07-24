package com.yoima.moddeck.client.widget;

import com.yoima.moddeck.client.theme.DeckTheme;
import com.yoima.moddeck.client.theme.DeckIcons;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class SearchFieldWidget extends EditBox {
    private static final int ICON_SIZE = 18;
    private static final int TEXT_INSET = 33;
    private final Font uiFont;

    public SearchFieldWidget(Font font, int x, int y, int width, String value, Consumer<String> responder) {
        this(font, x, y, width, value, Component.translatable("moddeck.search"), responder);
    }

    public SearchFieldWidget(Font font, int x, int y, int width, String value,
                             Component hint, Consumer<String> responder) {
        super(font, x, y, width, 30, hint);
        uiFont = font;
        setBordered(false);
        setTextShadow(false);
        setMaxLength(80);
        setHint(hint);
        setTextColor(DeckTheme.TEXT);
        setTextColorUneditable(DeckTheme.TEXT_MUTED);
        setValue(value);
        setResponder(responder);
    }

    @Override public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        DeckTheme.border(graphics, getX(), getY(), getWidth(), getHeight(), 6,
                isFocused() ? DeckTheme.ACCENT_DARK : DeckTheme.DIVIDER,
                isHoveredOrFocused() ? DeckTheme.FIELD_HOVER : DeckTheme.PANEL_RAISED);
        // The custom border requires an unbordered EditBox. Shift its complete text layer so
        // entered text, selection, cursor, and hint share the same centered baseline.
        graphics.pose().pushMatrix();
        graphics.pose().translate(TEXT_INSET, (getHeight() - 8) / 2.0f);
        // EditBox keeps this flag as mutable widget state. Re-assert it at render time so a
        // theme/widget refresh cannot restore vanilla's black text shadow in the search field.
        setTextShadow(false);
        super.extractWidgetRenderState(graphics, mouseX, mouseY, delta);
        graphics.pose().popMatrix();
        DeckIcons.draw(graphics, DeckIcons.Icon.SEARCH, getX() + 9,
                getY() + (getHeight() - ICON_SIZE) / 2, ICON_SIZE, DeckTheme.TEXT_SECONDARY);
    }

    @Override public void onClick(MouseButtonEvent event, boolean doubleClick) {
        // EditBox calculates the caret from its unshifted private textX. Mirror the render inset
        // here so clicking within text still chooses the expected character.
        super.onClick(new MouseButtonEvent(event.x() - TEXT_INSET, event.y(), event.buttonInfo()), doubleClick);
    }

    @Override
    public void setTextShadow(boolean ignored) {
        // The current Mod Deck UI uses flat, high-contrast text. SearchFieldWidget must not
        // inherit vanilla's shadow when EditBox updates its internal render state.
        super.setTextShadow(false);
    }

    private Font getFont() {
        return uiFont;
    }
}
