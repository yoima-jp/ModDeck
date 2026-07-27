package com.yoima.moddeck.client.widget;

import com.yoima.moddeck.api.option.ButtonOption;
import com.yoima.moddeck.client.theme.DeckFonts;
import com.yoima.moddeck.client.theme.DeckTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;

/** Compact action control for a {@link ButtonOption}. */
public final class ButtonOptionWidget extends AbstractWidget {
    private final ButtonOption option;

    public ButtonOptionWidget(int x, int y, int width, ButtonOption option) {
        super(x, y, width, 28, option.buttonText().component());
        this.option = option;
    }

    @Override public void onClick(MouseButtonEvent event, boolean doubleClick) {
        option.runAction();
    }

    @Override protected void extractWidgetRenderState(GuiGraphicsExtractor graphics,
                                                        int mouseX, int mouseY, float delta) {
        int fill = active && isHoveredOrFocused() ? DeckTheme.FIELD_HOVER : DeckTheme.PANEL_RAISED;
        DeckTheme.border(graphics, getX(), getY(), getWidth(), getHeight(), 5,
                DeckTheme.DIVIDER, fill);
        DeckTheme.centeredText(graphics, DeckFonts.ui(), getMessage(),
                getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2,
                active ? DeckTheme.TEXT : DeckTheme.TEXT_MUTED);
    }

    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
