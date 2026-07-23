package com.yoima.moddeck.client.widget;

import com.yoima.moddeck.api.option.BooleanOption;
import com.yoima.moddeck.client.theme.DeckTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Material-like switch with a state-independent shape and narration label. */
public final class SwitchWidget extends AbstractWidget {
    private final BooleanOption option;
    private final Runnable onChanged;

    public SwitchWidget(int x, int y, BooleanOption option, Runnable onChanged) {
        super(x, y, 34, 18, option.displayNameText().component());
        this.option = option;
        this.onChanged = onChanged;
        active = option.editable();
    }

    @Override public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (option.trySetValue(!option.value())) onChanged.run();
    }

    @Override protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int track = option.value() ? DeckTheme.ACCENT_DARK : DeckTheme.FIELD;
        if (isHoveredOrFocused()) track = option.value() ? DeckTheme.ACCENT : DeckTheme.FIELD_HOVER;
        DeckTheme.roundedRect(graphics, getX(), getY(), getWidth(), getHeight(), 9, track);
        int knobX = option.value() ? getX() + 19 : getX() + 3;
        DeckTheme.roundedRect(graphics, knobX, getY() + 3, 12, 12, 6,
                option.value() ? DeckTheme.TEXT : DeckTheme.TEXT_SECONDARY);
    }

    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
