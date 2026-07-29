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
    private static final int SCROLL_BAR_WIDTH = 5;
    private static final int SCROLL_ARROW_ZONE = 14;
    private final EnumOption<?> option;
    private final Runnable onChanged;
    private boolean expanded;
    private int viewportTop = Integer.MIN_VALUE;
    private int viewportBottom = Integer.MAX_VALUE;
    private int scrollOffset;
    private boolean draggingScrollbar;

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
        graphics.text(font, enumLabel(option.draftValue()), getX() + 12, getY() + 10, DeckTheme.TEXT, false);
        DeckIcons.draw(graphics, expanded ? DeckIcons.Icon.CHEVRON_UP : DeckIcons.Icon.CHEVRON_DOWN,
                getRight() - 19, getY() + 7, 14, DeckTheme.TEXT_SECONDARY);
    }

    @Override public void extractPopupRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (!expanded) return;
        var font = DeckFonts.ui();
        int menuY = menuY();
        int fullHeight = option.values().size() * ITEM_HEIGHT;
        int menuHeight = boundedMenuHeight(fullHeight);
        DeckTheme.border(graphics, getX(), menuY, getWidth(), menuHeight, 5,
                DeckTheme.DIVIDER, DeckTheme.PANEL_RAISED);
        int maxScroll = Math.max(0, fullHeight - menuHeight);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
        graphics.enableScissor(getX(), menuY, getRight(), menuY + menuHeight);
        for (int index = 0; index < option.values().size(); index++) {
            Enum<?> value = option.values().get(index);
            int itemY = menuY + index * ITEM_HEIGHT - scrollOffset;
            if (itemY + ITEM_HEIGHT <= menuY || itemY >= menuY + menuHeight) continue;
            boolean selected = value == option.draftValue();
            if (selected) {
                DeckTheme.roundedRect(graphics, getX() + 3, itemY + 2, getWidth() - 6, ITEM_HEIGHT - 4,
                        3, DeckTheme.ACCENT_DARK);
            }
            graphics.text(font, enumLabel(value), getX() + 12, itemY + 8,
                    selected ? DeckTheme.TEXT : DeckTheme.TEXT_SECONDARY, false);
            if (selected) DeckIcons.draw(graphics, DeckIcons.Icon.CHECK,
                    getRight() - 18, itemY + 5, 13, DeckTheme.TEXT);
        }
        graphics.disableScissor();
        drawScrollbar(graphics, menuY, menuHeight, fullHeight);
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
        int fullHeight = option.values().size() * ITEM_HEIGHT;
        int menuHeight = boundedMenuHeight(fullHeight);
        boolean overControl = isMouseOver(mouseX, mouseY);
        boolean overMenu = mouseX >= getX() && mouseX < getRight() && mouseY >= menuY
                && mouseY < menuY + menuHeight;
        if (!overControl && !overMenu) expanded = false;
    }

    public boolean handleExpandedClick(double mouseX, double mouseY) {
        if (!expanded) return false;
        int menuY = menuY();
        int fullHeight = option.values().size() * ITEM_HEIGHT;
        int menuHeight = boundedMenuHeight(fullHeight);
        if (mouseX < getX() || mouseX >= getRight() || mouseY < menuY
                || mouseY >= menuY + menuHeight) {
            return false;
        }
        if (isScrollbarHit(mouseX, mouseY, menuY, menuHeight, fullHeight)) {
            draggingScrollbar = true;
            return true;
        }
        int maxScroll = Math.max(0, fullHeight - menuHeight);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
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
        if (index < 0 || index >= option.values().size()) return false;
        setEnumValue(index);
        expanded = false;
        onChanged.run();
        return true;
    }

    @Override public boolean handleExpandedDrag(double mouseX, double mouseY) {
        if (!expanded || !draggingScrollbar) return false;
        int fullHeight = option.values().size() * ITEM_HEIGHT;
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
        int fullHeight = option.values().size() * ITEM_HEIGHT;
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
        int fullHeight = option.values().size() * ITEM_HEIGHT;
        int height = boundedMenuHeight(fullHeight);
        int below = getBottom() + 2;
        if (below + height <= viewportBottom) return below;
        return Math.max(viewportTop, getY() - height - 2);
    }

    /** Caps popup height to the available viewport so large enum lists stay usable. */
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

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void setEnumValue(int index) {
        EnumOption raw = option;
        raw.trySetDraftValue((Enum) option.values().get(index));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Component enumLabel(Enum<?> value) {
        EnumOption raw = option;
        return ((com.yoima.moddeck.api.ConfigText) raw.label(value)).component();
    }
}
