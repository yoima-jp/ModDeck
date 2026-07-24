package com.yoima.moddeck.client.widget;

import com.yoima.moddeck.api.option.ConfigOption;
import com.yoima.moddeck.client.theme.DeckIcons;
import com.yoima.moddeck.client.theme.DeckTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Compact per-entry reset action that appears only when the draft differs from its default. */
public final class OptionResetWidget extends AbstractWidget {
    private final ConfigOption<?> option;
    private final Runnable onReset;

    public OptionResetWidget(int x, int y, ConfigOption<?> option, Runnable onReset) {
        super(x, y, 22, 22, Component.translatable("moddeck.reset_option", option.displayNameText().component()));
        this.option = option;
        this.onReset = onReset;
    }

    public void refreshState() {
        visible = option.canResetDraft() && option.isDisplayed();
        active = visible && option.editable() && option.isEnabled();
    }

    @Override public void onClick(MouseButtonEvent event, boolean doubleClick) {
        option.reset();
        refreshState();
        // The draft value was just mutated. Ask the screen to rebuild widgets on the next tick
        // so every widget (sliders, switches, text fields, selectors, keybinds, lists, colors)
        // is recreated from the current draft. Rebuilding inside child event iteration is unsafe,
        // so the callback must be deferred rather than immediate.
        onReset.run();
    }

    @Override protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (!visible) return;
        DeckTheme.roundedRect(graphics, getX(), getY(), getWidth(), getHeight(), 5,
                isHoveredOrFocused() ? DeckTheme.FIELD_HOVER : DeckTheme.FIELD);
        DeckIcons.draw(graphics, DeckIcons.Icon.RESET, getX() + 4, getY() + 4, 14, DeckTheme.TEXT_SECONDARY);
    }

    @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
}
