package com.yoima.moddeck.client.widget;

import com.yoima.moddeck.api.option.StringOption;
import com.yoima.moddeck.client.theme.DeckTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class TextFieldWidget extends EditBox {
    private static final int TEXT_INSET = 11;

    public TextFieldWidget(Font font, int x, int y, int width, StringOption option, Runnable onChanged) {
        // Give EditBox the real text viewport instead of translating a full-width viewport at
        // render time. Its caret hit-testing, selection drag and horizontal scroll then all use
        // the same bounds, including when moving back toward the end of a long value.
        super(font, x + TEXT_INSET, y, Math.max(1, width - TEXT_INSET * 2), 28,
                option.displayNameText().component());
        setBordered(false);
        setTextShadow(false);
        setMaxLength(option.maximumLength());
        setValue(option.draftValue());
        setTextColor(DeckTheme.TEXT);
        setTextColorUneditable(DeckTheme.TEXT_MUTED);
        setResponder(value -> {
            if (option.trySetDraftValue(value)) onChanged.run();
        });
        active = option.editable();
    }

    @Override public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        DeckTheme.border(graphics, getX() - TEXT_INSET, getY(), getWidth() + TEXT_INSET * 2, getHeight(), 5,
                isFocused() ? DeckTheme.ACCENT_DARK : DeckTheme.DIVIDER,
                isHoveredOrFocused() ? DeckTheme.FIELD_HOVER : DeckTheme.FIELD);
        // In 26.3 an unbordered EditBox pins textY to getY(). The visual border is custom, so
        // translate only the vanilla text/cursor layer to retain the theme and center the line.
        graphics.pose().pushMatrix();
        graphics.pose().translate(0, (getHeight() - 8) / 2.0f);
        super.extractWidgetRenderState(graphics, mouseX, mouseY, delta);
        graphics.pose().popMatrix();
    }

    @Override protected void onDrag(MouseButtonEvent event, double dragX, double dragY) {
        // Vanilla 26.3 clamps a drag at the right edge to the end of the currently visible slice.
        // Because that position is still considered visible, EditBox does not advance displayPos;
        // left-edge dragging works while right-edge dragging stalls. Explicitly move one codepoint
        // beyond either edge so EditBox#scrollTo updates the viewport symmetrically.
        if (event.x() >= getX() + getWidth() && getCursorPosition() < getValue().length()) {
            moveCursor(1, true);
            return;
        }
        if (event.x() <= getX() && getCursorPosition() > 0) {
            moveCursor(-1, true);
            return;
        }
        super.onDrag(event, dragX, dragY);
    }
}
