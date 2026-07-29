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
    private static final int SCROLL_BAR_WIDTH = 5;
    private static final int SCROLL_ARROW_ZONE = 14;
    private final SelectorOption<T> option;
    private final Runnable onChanged;
    private boolean expanded;
    private int viewportTop = Integer.MIN_VALUE;
    private int viewportBottom = Integer.MAX_VALUE;
    private int scrollOffset;
    private boolean draggingScrollbar;

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
        graphics.text(font, option.label(option.draftValue()).component(), getX() + 12, getY() + 10, DeckTheme.TEXT, false);
        DeckIcons.draw(graphics, expanded ? DeckIcons.Icon.CHEVRON_UP : DeckIcons.Icon.CHEVRON_DOWN,
                getRight() - 19, getY() + 7, 14, DeckTheme.TEXT_SECONDARY);
    }

    @Override public void extractPopupRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (!expanded) return;
        var font = DeckFonts.ui();
        int menuY = menuY();
        int fullHeight = option.choices().size() * ITEM_HEIGHT;
        int menuHeight = boundedMenuHeight(fullHeight);
        DeckTheme.border(graphics, getX(), menuY, getWidth(), menuHeight, 5,
                DeckTheme.DIVIDER, DeckTheme.PANEL_RAISED);
        // Clamp scroll before rendering so the list never overshoots after viewport changes.
        int maxScroll = Math.max(0, fullHeight - menuHeight);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
        graphics.enableScissor(getX(), menuY, getRight(), menuY + menuHeight);
        for (int index = 0; index < option.choices().size(); index++) {
            T choice = option.choices().get(index);
            int itemY = menuY + index * ITEM_HEIGHT - scrollOffset;
            if (itemY + ITEM_HEIGHT <= menuY || itemY >= menuY + menuHeight) continue;
            boolean selected = choice.equals(option.draftValue());
            if (selected) DeckTheme.roundedRect(graphics, getX() + 3, itemY + 2, getWidth() - 6,
                    ITEM_HEIGHT - 4, 3, DeckTheme.ACCENT_DARK);
            graphics.text(font, option.label(choice).component(), getX() + 12, itemY + 8,
                    selected ? DeckTheme.TEXT : DeckTheme.TEXT_SECONDARY, false);
        }
        graphics.disableScissor();
        drawScrollbar(graphics, menuY, menuHeight, fullHeight);
    }

    @Override public void setPopupViewport(int top, int bottom) {
        viewportTop = top;
        viewportBottom = bottom;
    }

    @Override public boolean isExpanded() { return expanded; }

    @Override public boolean handleExpandedClick(double mouseX, double mouseY) {
        if (!expanded) return false;
        int menuY = menuY();
        int fullHeight = option.choices().size() * ITEM_HEIGHT;
        int menuHeight = boundedMenuHeight(fullHeight);
        if (mouseX < getX() || mouseX >= getRight() || mouseY < menuY
                || mouseY >= menuY + menuHeight) return false;
        // Clicks on the scrollbar area initiate scrollbar drag instead of selecting an item.
        if (isScrollbarHit(mouseX, mouseY, menuY, menuHeight, fullHeight)) {
            draggingScrollbar = true;
            return true;
        }
        int maxScroll = Math.max(0, fullHeight - menuHeight);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
        // Scroll arrow zones at the top and bottom of the popup act as page-up/page-down.
        if (canScroll(menuHeight, fullHeight) && isScrollbarColumn(mouseX)) {
            if (mouseY < menuY + SCROLL_ARROW_ZONE && scrollOffset > 0) {
                scrollOffset = Math.max(0, scrollOffset - menuHeight + ITEM_HEIGHT);
                return true;
            }
            if (mouseY >= menuY + menuHeight - SCROLL_ARROW_ZONE && scrollOffset < maxScroll) {
                scrollOffset = Math.min(maxScroll, scrollOffset + menuHeight - ITEM_HEIGHT);
                return true;
            }
        }
        int index = (int) ((mouseY - menuY + scrollOffset) / ITEM_HEIGHT);
        if (index < 0 || index >= option.choices().size()) return false;
        T selected = option.choices().get(index);
        if (option.trySetDraftValue(selected)) onChanged.run();
        expanded = false;
        return true;
    }

    @Override public void collapseIfOutside(double mouseX, double mouseY) {
        int menuY = menuY();
        int fullHeight = option.choices().size() * ITEM_HEIGHT;
        int menuHeight = boundedMenuHeight(fullHeight);
        boolean overMenu = mouseX >= getX() && mouseX < getRight() && mouseY >= menuY
                && mouseY < menuY + menuHeight;
        if (!isMouseOver(mouseX, mouseY) && !overMenu) expanded = false;
    }

    @Override public boolean handleExpandedDrag(double mouseX, double mouseY) {
        if (!expanded || !draggingScrollbar) return false;
        int fullHeight = option.choices().size() * ITEM_HEIGHT;
        int menuHeight = boundedMenuHeight(fullHeight);
        if (!canScroll(menuHeight, fullHeight)) return true;
        int trackTop = menuY() + SCROLL_ARROW_ZONE;
        int trackBottom = menuY() + menuHeight - SCROLL_ARROW_ZONE;
        int trackHeight = trackBottom - trackTop;
        int maxScroll = fullHeight - menuHeight;
        float ratio = trackHeight > 0 ? (float) (mouseY - trackTop) / trackHeight : 0;
        ratio = Math.max(0, Math.min(1, ratio));
        scrollOffset = Math.round(ratio * maxScroll);
        return true;
    }

    @Override public void handleExpandedRelease() {
        draggingScrollbar = false;
    }

    @Override public boolean handleExpandedScroll(double mouseX, double mouseY, double scrollY) {
        if (!expanded) return false;
        int menuY = menuY();
        int fullHeight = option.choices().size() * ITEM_HEIGHT;
        int menuHeight = boundedMenuHeight(fullHeight);
        if (!canScroll(menuHeight, fullHeight)) return false;
        if (mouseX < getX() || mouseX >= getRight() || mouseY < menuY
                || mouseY >= menuY + menuHeight) return false;
        int maxScroll = fullHeight - menuHeight;
        int next = Math.max(0, Math.min(maxScroll, scrollOffset - (int) Math.round(scrollY * ITEM_HEIGHT)));
        if (next == scrollOffset) return false;
        scrollOffset = next;
        return true;
    }

    private int menuY() {
        int fullHeight = option.choices().size() * ITEM_HEIGHT;
        int height = boundedMenuHeight(fullHeight);
        int below = getBottom() + 2;
        if (below + height <= viewportBottom) return below;
        return Math.max(viewportTop, getY() - height - 2);
    }

    /** Caps popup height to the available viewport so large choice lists stay usable. */
    private int boundedMenuHeight(int fullHeight) {
        int available = Math.max(ITEM_HEIGHT * 3, viewportBottom - viewportTop - 4);
        return Math.min(fullHeight, available);
    }

    private boolean canScroll(int menuHeight, int fullHeight) {
        return fullHeight > menuHeight;
    }

    private boolean isScrollbarHit(double mouseX, double mouseY, int menuY, int menuHeight, int fullHeight) {
        if (!canScroll(menuHeight, fullHeight)) return false;
        return isScrollbarColumn(mouseX)
                && mouseY >= menuY + SCROLL_ARROW_ZONE && mouseY < menuY + menuHeight - SCROLL_ARROW_ZONE;
    }

    private boolean isScrollbarColumn(double mouseX) {
        return mouseX >= getRight() - SCROLL_BAR_WIDTH - 2 && mouseX < getRight() - 2;
    }

    private void drawScrollbar(GuiGraphicsExtractor graphics, int menuY, int menuHeight, int fullHeight) {
        if (!canScroll(menuHeight, fullHeight)) return;
        int trackTop = menuY + SCROLL_ARROW_ZONE;
        int trackBottom = menuY + menuHeight - SCROLL_ARROW_ZONE;
        int trackHeight = trackBottom - trackTop;
        if (trackHeight <= 0) return;
        int maxScroll = fullHeight - menuHeight;
        float thumbRatio = (float) menuHeight / fullHeight;
        int thumbHeight = Math.max(20, Math.round(trackHeight * thumbRatio));
        float scrollRatio = maxScroll > 0 ? (float) scrollOffset / maxScroll : 0;
        int thumbY = trackTop + Math.round(scrollRatio * (trackHeight - thumbHeight));
        DeckIcons.draw(graphics, DeckIcons.Icon.CHEVRON_UP, getRight() - 14, menuY + 1, 12, DeckTheme.TEXT_SECONDARY);
        DeckIcons.draw(graphics, DeckIcons.Icon.CHEVRON_DOWN, getRight() - 14, menuY + menuHeight - 13, 12, DeckTheme.TEXT_SECONDARY);
        DeckTheme.roundedRect(graphics, getRight() - SCROLL_BAR_WIDTH - 1, thumbY, SCROLL_BAR_WIDTH, thumbHeight, 2, DeckTheme.TEXT_MUTED);
    }

    @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
}
