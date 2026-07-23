package com.yoima.moddeck.client.widget;

import com.yoima.moddeck.api.option.EnumOption;
import com.yoima.moddeck.client.theme.DeckTheme;
import com.yoima.moddeck.client.theme.DeckFonts;
import com.yoima.moddeck.client.theme.DeckIcons;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Selector that expands into an in-place themed menu rather than cycling invisibly. */
public final class EnumSelectorWidget extends AbstractWidget implements ExpandableOptionWidget {
    private static final int ITEM_HEIGHT = 22;
    private final EnumOption<?> option;
    private final Runnable onChanged;
    private boolean expanded;
    private int viewportTop = Integer.MIN_VALUE;
    private int viewportBottom = Integer.MAX_VALUE;

    public EnumSelectorWidget(int x, int y, int width, EnumOption<?> option, Runnable onChanged, boolean opensUp) {
        super(x, y, width, 28, option.displayNameText().component());
        this.option = option;
        this.onChanged = onChanged;
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!active || !visible) return false;
        double mouseX = event.x();
        double mouseY = event.y();
        if (handleExpandedClick(mouseX, mouseY)) return true;
        if (isMouseOver(mouseX, mouseY)) {
            expanded = !expanded;
            return true;
        }
        expanded = false;
        return false;
    }

    @Override protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        DeckTheme.border(graphics, getX(), getY(), getWidth(), getHeight(), 5,
                expanded || isHoveredOrFocused() ? DeckTheme.ACCENT_DARK : DeckTheme.DIVIDER, DeckTheme.FIELD);
        var font = DeckFonts.ui();
        graphics.text(font, enumLabel(option.value()), getX() + 12, getY() + 10, DeckTheme.TEXT, false);
        DeckIcons.draw(graphics, expanded ? DeckIcons.Icon.CHEVRON_UP : DeckIcons.Icon.CHEVRON_DOWN,
                getRight() - 19, getY() + 7, 14, DeckTheme.TEXT_SECONDARY);
    }

    @Override public void extractPopupRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (!expanded) return;
        var font = DeckFonts.ui();
        int menuY = menuY();
        int menuHeight = option.values().size() * ITEM_HEIGHT;
        DeckTheme.border(graphics, getX(), menuY, getWidth(), menuHeight, 5,
                DeckTheme.DIVIDER, DeckTheme.PANEL_RAISED);
        for (int index = 0; index < option.values().size(); index++) {
            Enum<?> value = option.values().get(index);
            int itemY = menuY + index * ITEM_HEIGHT;
            boolean selected = value == option.value();
            if (selected) {
                DeckTheme.roundedRect(graphics, getX() + 3, itemY + 2, getWidth() - 6, ITEM_HEIGHT - 4,
                        3, DeckTheme.ACCENT_DARK);
            }
            graphics.text(font, enumLabel(value), getX() + 12, itemY + 8,
                    selected ? DeckTheme.TEXT : DeckTheme.TEXT_SECONDARY, false);
            if (selected) DeckIcons.draw(graphics, DeckIcons.Icon.CHECK,
                    getRight() - 18, itemY + 5, 13, DeckTheme.TEXT);
        }
    }

    @Override public void setPopupViewport(int top, int bottom) {
        viewportTop = top;
        viewportBottom = bottom;
    }

    @Override public boolean isExpanded() { return expanded; }

    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    public void collapseIfOutside(double mouseX, double mouseY) {
        if (!expanded) return;
        int menuY = menuY();
        boolean overControl = isMouseOver(mouseX, mouseY);
        boolean overMenu = mouseX >= getX() && mouseX < getRight() && mouseY >= menuY
                && mouseY < menuY + option.values().size() * ITEM_HEIGHT;
        if (!overControl && !overMenu) expanded = false;
    }

    public boolean handleExpandedClick(double mouseX, double mouseY) {
        if (!expanded) return false;
        int menuY = menuY();
        if (mouseX < getX() || mouseX >= getRight() || mouseY < menuY
                || mouseY >= menuY + option.values().size() * ITEM_HEIGHT) {
            return false;
        }
        int index = (int) ((mouseY - menuY) / ITEM_HEIGHT);
        setEnumValue(index);
        expanded = false;
        onChanged.run();
        return true;
    }

    private int menuY() {
        int height = option.values().size() * ITEM_HEIGHT;
        int below = getBottom() + 2;
        if (below + height <= viewportBottom) return below;
        return Math.max(viewportTop, getY() - height - 2);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void setEnumValue(int index) {
        EnumOption raw = option;
        raw.trySetValue((Enum) option.values().get(index));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Component enumLabel(Enum<?> value) {
        EnumOption raw = option;
        return ((com.yoima.moddeck.api.ConfigText) raw.label(value)).component();
    }
}
