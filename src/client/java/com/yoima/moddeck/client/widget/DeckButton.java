package com.yoima.moddeck.client.widget;

import com.yoima.moddeck.client.theme.DeckTheme;
import com.yoima.moddeck.client.theme.DeckFonts;
import com.yoima.moddeck.client.theme.DeckIcons;
import java.util.Objects;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class DeckButton extends AbstractWidget {
    public enum Style { PRIMARY, SECONDARY, ICON, THEME }

    private final Runnable action;
    private final Style style;

    public DeckButton(int x, int y, int width, int height, Component message, Style style, Runnable action) {
        super(x, y, width, height, message);
        this.style = Objects.requireNonNull(style, "style");
        this.action = Objects.requireNonNull(action, "action");
    }

    @Override public void onClick(MouseButtonEvent event, boolean doubleClick) {
        action.run();
    }

    @Override protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int fill = switch (style) {
            case PRIMARY -> isHoveredOrFocused() ? 0xFF967CF0 : DeckTheme.ACCENT_DARK;
            case SECONDARY -> isHoveredOrFocused() ? DeckTheme.FIELD_HOVER : DeckTheme.PANEL_RAISED;
            case ICON -> isHoveredOrFocused() ? DeckTheme.FIELD_HOVER : DeckTheme.FIELD;
            case THEME -> isHoveredOrFocused() ? DeckTheme.PANEL_RAISED : DeckTheme.BACKGROUND_TOP;
        };
        if (style == Style.THEME) {
            DeckTheme.border(graphics, getX(), getY(), getWidth(), getHeight(), 5, DeckTheme.DIVIDER, fill);
        } else {
            DeckTheme.roundedRect(graphics, getX(), getY(), getWidth(), getHeight(), 5, fill);
        }
        var font = DeckFonts.ui();
        if (style == Style.PRIMARY || style == Style.SECONDARY) {
            DeckIcons.Icon icon = style == Style.PRIMARY ? DeckIcons.Icon.SAVE : DeckIcons.Icon.RESET;
            DeckIcons.draw(graphics, icon, getX() + 10, getY() + 6, 16, DeckTheme.TEXT);
            graphics.centeredText(font, getMessage(), getX() + getWidth() / 2 + 5,
                    getY() + (getHeight() - 8) / 2, DeckTheme.TEXT);
        } else if (style == Style.ICON) {
            DeckIcons.draw(graphics, DeckIcons.Icon.CLOSE, getX() + 6, getY() + 6, 16, DeckTheme.TEXT);
        } else {
            graphics.centeredText(font, getMessage(), getX() + getWidth() / 2,
                    getY() + (getHeight() - 8) / 2, DeckTheme.TEXT);
        }
    }

    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
