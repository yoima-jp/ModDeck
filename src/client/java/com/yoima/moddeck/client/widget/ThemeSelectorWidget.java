package com.yoima.moddeck.client.widget;

import com.yoima.moddeck.client.theme.DeckFonts;
import com.yoima.moddeck.client.theme.DeckIcons;
import com.yoima.moddeck.client.theme.DeckTheme;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Theme selector with Automatic, Light, and Dark modes. */
public final class ThemeSelectorWidget extends AbstractWidget implements ExpandableOptionWidget {
    private static final int ITEM_HEIGHT = 25;
    private final Consumer<DeckTheme.Mode> onChanged;
    private boolean expanded;

    public ThemeSelectorWidget(int x, int y, int width, Consumer<DeckTheme.Mode> onChanged) {
        super(x, y, width, 28, Component.translatable("moddeck.theme.select"));
        this.onChanged = Objects.requireNonNull(onChanged, "onChanged");
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!active || !visible) return false;
        if (handleExpandedClick(event.x(), event.y())) return true;
        if (isMouseOver(event.x(), event.y())) {
            expanded = !expanded;
            return true;
        }
        expanded = false;
        return false;
    }

    @Override protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                                       float delta) {
        DeckTheme.border(graphics, getX(), getY(), getWidth(), getHeight(), 5,
                expanded || isHoveredOrFocused() ? DeckTheme.ACCENT_DARK : DeckTheme.DIVIDER,
                DeckTheme.BACKGROUND_TOP);
        drawMode(graphics, DeckTheme.mode(), getX() + 7, getY() + 6, false);
        DeckIcons.draw(graphics, expanded ? DeckIcons.Icon.CHEVRON_UP : DeckIcons.Icon.CHEVRON_DOWN,
                getRight() - 18, getY() + 7, 14, DeckTheme.TEXT_SECONDARY);
    }

    @Override public void extractPopupRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (!expanded) return;
        int menuY = getBottom() + 3;
        DeckTheme.border(graphics, getX(), menuY, getWidth(), DeckTheme.Mode.values().length * ITEM_HEIGHT,
                5, DeckTheme.DIVIDER, DeckTheme.PANEL_RAISED);
        for (int index = 0; index < DeckTheme.Mode.values().length; index++) {
            DeckTheme.Mode mode = DeckTheme.Mode.values()[index];
            int itemY = menuY + index * ITEM_HEIGHT;
            if (mode == DeckTheme.mode()) {
                DeckTheme.roundedRect(graphics, getX() + 3, itemY + 2, getWidth() - 6,
                        ITEM_HEIGHT - 4, 3, DeckTheme.ACCENT_DARK);
            }
            drawMode(graphics, mode, getX() + 7, itemY + 5, true);
            if (mode == DeckTheme.mode()) {
                DeckIcons.draw(graphics, DeckIcons.Icon.CHECK, getRight() - 18, itemY + 6,
                        13, DeckTheme.TEXT);
            }
        }
    }

    public boolean handleExpandedClick(double mouseX, double mouseY) {
        if (!expanded) return false;
        int menuY = getBottom() + 3;
        if (mouseX < getX() || mouseX >= getRight() || mouseY < menuY
                || mouseY >= menuY + DeckTheme.Mode.values().length * ITEM_HEIGHT) {
            return false;
        }
        DeckTheme.Mode selected = DeckTheme.Mode.values()[(int) ((mouseY - menuY) / ITEM_HEIGHT)];
        expanded = false;
        onChanged.accept(selected);
        return true;
    }

    public void collapseIfOutside(double mouseX, double mouseY) {
        if (!expanded) return;
        int menuY = getBottom() + 3;
        boolean overMenu = mouseX >= getX() && mouseX < getRight() && mouseY >= menuY
                && mouseY < menuY + DeckTheme.Mode.values().length * ITEM_HEIGHT;
        if (!isMouseOver(mouseX, mouseY) && !overMenu) expanded = false;
    }

    @Override public void setPopupViewport(int top, int bottom) {}

    @Override public boolean isExpanded() { return expanded; }

    private void drawMode(GuiGraphicsExtractor graphics, DeckTheme.Mode mode, int x, int y, boolean menu) {
        DeckIcons.Icon icon = switch (mode) {
            case AUTO -> DeckIcons.Icon.MONITOR;
            case LIGHT -> DeckIcons.Icon.SUN;
            case DARK -> DeckIcons.Icon.MOON;
        };
        int color = menu && mode != DeckTheme.mode() ? DeckTheme.TEXT_SECONDARY : DeckTheme.TEXT;
        DeckIcons.draw(graphics, icon, x, y, 15, color);
        graphics.text(DeckFonts.ui(), Component.translatable(mode.translationKey()), x + 21, y + 3, color, false);
    }

    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
