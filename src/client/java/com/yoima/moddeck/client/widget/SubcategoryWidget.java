package com.yoima.moddeck.client.widget;

import com.yoima.moddeck.api.option.SubcategoryOption;
import com.yoima.moddeck.client.theme.DeckFonts;
import com.yoima.moddeck.client.theme.DeckIcons;
import com.yoima.moddeck.client.theme.DeckTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;

public final class SubcategoryWidget extends AbstractWidget {
    private final SubcategoryOption option;
    private final Runnable onChanged;

    public SubcategoryWidget(int x, int y, int width, SubcategoryOption option, Runnable onChanged) {
        super(x, y, width, 28, option.displayNameText().component());
        this.option = option;
        this.onChanged = onChanged;
    }

    @Override public void onClick(MouseButtonEvent event, boolean doubleClick) {
        option.setDraftValue(!option.draftValue());
        onChanged.run();
    }

    @Override protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        DeckTheme.border(graphics, getX(), getY(), getWidth(), getHeight(), 5,
                isHoveredOrFocused() ? DeckTheme.ACCENT_DARK : DeckTheme.DIVIDER, DeckTheme.FIELD);
        graphics.text(DeckFonts.ui(), option.displayNameText().component(), getX() + 10, getY() + 10,
                DeckTheme.TEXT, false);
        DeckIcons.draw(graphics, option.draftValue() ? DeckIcons.Icon.CHEVRON_UP : DeckIcons.Icon.CHEVRON_DOWN,
                getRight() - 20, getY() + 7, 14, DeckTheme.TEXT_SECONDARY);
    }

    @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
}
