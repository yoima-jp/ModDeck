package com.yoima.moddeck.client.widget;

import com.yoima.moddeck.api.option.ListOption;
import com.yoima.moddeck.client.screen.ModListScreen;
import net.minecraft.client.gui.screens.Screen;
import com.yoima.moddeck.client.theme.DeckFonts;
import com.yoima.moddeck.client.theme.DeckTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Compact summary that opens the full list editor without crowding the main option row. */
public final class ListFieldWidget<T> extends AbstractWidget {
    private final ListOption<T> option;
    private final Runnable onChanged;

    public ListFieldWidget(net.minecraft.client.gui.Font font, int x, int y, int width,
                           ListOption<T> option, Runnable onChanged) {
        super(x, y, width, 28, option.displayNameText().component());
        this.option = option;
        this.onChanged = onChanged;
        active = option.editable();
    }

    @Override public void onClick(MouseButtonEvent event, boolean doubleClick) {
        Screen screen = Minecraft.getInstance().gui.screen();
        if (screen instanceof ModListScreen modList) {
            modList.startListEditing(option);
        }
    }

    @Override protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        DeckTheme.border(graphics, getX(), getY(), getWidth(), getHeight(), 5,
                isHoveredOrFocused() ? DeckTheme.ACCENT_DARK : DeckTheme.DIVIDER, DeckTheme.FIELD);
        String summary = Component.translatable("moddeck.list.summary", option.draftValue().size()).getString();
        graphics.text(DeckFonts.ui(), summary, getX() + 11, getY() + 10, DeckTheme.TEXT, false);
        graphics.text(DeckFonts.ui(), "›", getX() + getWidth() - 16, getY() + 9, DeckTheme.TEXT_SECONDARY, false);
    }

    @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
}
