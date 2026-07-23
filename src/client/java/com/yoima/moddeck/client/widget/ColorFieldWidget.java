package com.yoima.moddeck.client.widget;

import com.yoima.moddeck.api.option.ColorOption;
import com.yoima.moddeck.client.screen.ColorPickerScreen;
import com.yoima.moddeck.client.theme.DeckFonts;
import com.yoima.moddeck.client.theme.DeckTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;

/** Color summary opening a focused picker, avoiding native widgets that clash with Mod Deck. */
public final class ColorFieldWidget extends AbstractWidget {
    private final ColorOption option;
    private final Runnable onChanged;

    public ColorFieldWidget(Font font, int x, int y, int width, ColorOption option, Runnable onChanged) {
        super(x, y, width, 28, option.displayNameText().component());
        this.option = option;
        this.onChanged = onChanged;
        active = option.editable();
    }

    @Override public void onClick(MouseButtonEvent event, boolean doubleClick) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreenAndShow(new ColorPickerScreen(minecraft.gui.screen(), option, onChanged));
    }

    @Override protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int swatch = option.alpha() ? option.draftValue() : 0xFF000000 | option.draftValue();
        DeckTheme.roundedRect(graphics, getX(), getY() + 4, 20, 20, 4, swatch);
        DeckTheme.border(graphics, getX() + 26, getY(), getWidth() - 26, getHeight(), 5,
                isHoveredOrFocused() ? DeckTheme.ACCENT_DARK : DeckTheme.DIVIDER, DeckTheme.FIELD);
        graphics.text(DeckFonts.ui(), "#" + option.draftHexValue(), getX() + 38, getY() + 10, DeckTheme.TEXT, false);
    }

    @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
}
