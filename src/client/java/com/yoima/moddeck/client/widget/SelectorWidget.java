package com.yoima.moddeck.client.widget;

import com.yoima.moddeck.api.option.SelectorOption;
import com.yoima.moddeck.client.theme.DeckFonts;
import com.yoima.moddeck.client.theme.DeckIcons;
import com.yoima.moddeck.client.theme.DeckTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;

public final class SelectorWidget<T> extends AbstractWidget implements ExpandableOptionWidget {
    private static final int ITEM_HEIGHT = 22;
    private final SelectorOption<T> option;
    private final Runnable onChanged;
    private boolean expanded;
    private int viewportTop = Integer.MIN_VALUE;
    private int viewportBottom = Integer.MAX_VALUE;

    public SelectorWidget(int x, int y, int width, SelectorOption<T> option, Runnable onChanged, boolean opensUp) {
        super(x, y, width, 28, option.displayNameText().component());
        this.option = option;
        this.onChanged = onChanged;
        active = option.editable();
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!active || !visible) return false;
        if (handleExpandedClick(event.x(), event.y())) return true;
        if (isMouseOver(event.x(), event.y())) { expanded = !expanded; return true; }
        expanded = false;
        return false;
    }

    @Override protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        DeckTheme.border(graphics, getX(), getY(), getWidth(), getHeight(), 5,
                expanded || isHoveredOrFocused() ? DeckTheme.ACCENT_DARK : DeckTheme.DIVIDER, DeckTheme.FIELD);
        var font = DeckFonts.ui();
        graphics.text(font, option.label(option.value()).component(), getX() + 12, getY() + 10, DeckTheme.TEXT, false);
        DeckIcons.draw(graphics, expanded ? DeckIcons.Icon.CHEVRON_UP : DeckIcons.Icon.CHEVRON_DOWN,
                getRight() - 19, getY() + 7, 14, DeckTheme.TEXT_SECONDARY);
    }

    @Override public void extractPopupRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (!expanded) return;
        var font = DeckFonts.ui();
        int menuY = menuY();
        DeckTheme.border(graphics, getX(), menuY, getWidth(), option.choices().size() * ITEM_HEIGHT, 5,
                DeckTheme.DIVIDER, DeckTheme.PANEL_RAISED);
        for (int index = 0; index < option.choices().size(); index++) {
            T choice = option.choices().get(index);
            int itemY = menuY + index * ITEM_HEIGHT;
            boolean selected = choice.equals(option.value());
            if (selected) DeckTheme.roundedRect(graphics, getX() + 3, itemY + 2, getWidth() - 6,
                    ITEM_HEIGHT - 4, 3, DeckTheme.ACCENT_DARK);
            graphics.text(font, option.label(choice).component(), getX() + 12, itemY + 8,
                    selected ? DeckTheme.TEXT : DeckTheme.TEXT_SECONDARY, false);
        }
    }

    @Override public void setPopupViewport(int top, int bottom) {
        viewportTop = top;
        viewportBottom = bottom;
    }

    @Override public boolean isExpanded() { return expanded; }

    @Override public boolean handleExpandedClick(double mouseX, double mouseY) {
        if (!expanded) return false;
        int menuY = menuY();
        if (mouseX < getX() || mouseX >= getRight() || mouseY < menuY
                || mouseY >= menuY + option.choices().size() * ITEM_HEIGHT) return false;
        T selected = option.choices().get((int) ((mouseY - menuY) / ITEM_HEIGHT));
        if (option.trySetValue(selected)) onChanged.run();
        expanded = false;
        return true;
    }

    @Override public void collapseIfOutside(double mouseX, double mouseY) {
        int menuY = menuY();
        boolean overMenu = mouseX >= getX() && mouseX < getRight() && mouseY >= menuY
                && mouseY < menuY + option.choices().size() * ITEM_HEIGHT;
        if (!isMouseOver(mouseX, mouseY) && !overMenu) expanded = false;
    }

    private int menuY() {
        int height = option.choices().size() * ITEM_HEIGHT;
        int below = getBottom() + 2;
        if (below + height <= viewportBottom) return below;
        return Math.max(viewportTop, getY() - height - 2);
    }

    @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
}
