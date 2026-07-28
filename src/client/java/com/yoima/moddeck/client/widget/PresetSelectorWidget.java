package com.yoima.moddeck.client.widget;

import com.yoima.moddeck.api.ConfigPreset;
import com.yoima.moddeck.client.theme.DeckFonts;
import com.yoima.moddeck.client.theme.DeckIcons;
import com.yoima.moddeck.client.theme.DeckTheme;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Fixed-footer picker for presets supplied by the selected mod. */
public final class PresetSelectorWidget extends AbstractWidget implements ExpandableOptionWidget {
    private static final int ITEM_HEIGHT = 24;
    private final List<ConfigPreset> presets;
    private final Consumer<ConfigPreset> onSelected;
    private boolean expanded;
    private int viewportTop = Integer.MIN_VALUE;
    private int viewportBottom = Integer.MAX_VALUE;

    public PresetSelectorWidget(int x, int y, int width, List<ConfigPreset> presets,
                                Consumer<ConfigPreset> onSelected) {
        super(x, y, width, 27, Component.translatable("moddeck.preset.select"));
        this.presets = List.copyOf(presets);
        if (this.presets.isEmpty()) throw new IllegalArgumentException("presets must not be empty");
        this.onSelected = Objects.requireNonNull(onSelected, "onSelected");
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
                DeckTheme.PANEL_RAISED);
        var font = DeckFonts.ui();
        graphics.text(font, fitted(getMessage().getString(), getWidth() - 35),
                getX() + 11, getY() + 9, DeckTheme.TEXT, false);
        DeckIcons.draw(graphics, expanded ? DeckIcons.Icon.CHEVRON_DOWN : DeckIcons.Icon.CHEVRON_UP,
                getRight() - 20, getY() + 6, 15, DeckTheme.TEXT_SECONDARY);
    }

    @Override public void extractPopupRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                                  float delta) {
        if (!expanded) return;
        int menuY = menuY();
        int menuHeight = presets.size() * ITEM_HEIGHT;
        DeckTheme.border(graphics, getX(), menuY, getWidth(), menuHeight, 5,
                DeckTheme.DIVIDER, DeckTheme.PANEL_RAISED);
        var font = DeckFonts.ui();
        for (int index = 0; index < presets.size(); index++) {
            ConfigPreset preset = presets.get(index);
            int itemY = menuY + index * ITEM_HEIGHT;
            if (mouseX >= getX() && mouseX < getRight()
                    && mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT) {
                DeckTheme.roundedRect(graphics, getX() + 3, itemY + 2,
                        getWidth() - 6, ITEM_HEIGHT - 4, 3, DeckTheme.FIELD_HOVER);
            }
            graphics.text(font, fitted(preset.displayText().component().getString(), getWidth() - 20),
                    getX() + 10, itemY + 8, DeckTheme.TEXT, false);
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
        int menuHeight = presets.size() * ITEM_HEIGHT;
        if (mouseX < getX() || mouseX >= getRight() || mouseY < menuY || mouseY >= menuY + menuHeight) {
            return false;
        }
        ConfigPreset selected = presets.get((int) ((mouseY - menuY) / ITEM_HEIGHT));
        expanded = false;
        onSelected.accept(selected);
        return true;
    }

    @Override public void collapseIfOutside(double mouseX, double mouseY) {
        int menuY = menuY();
        boolean overMenu = mouseX >= getX() && mouseX < getRight()
                && mouseY >= menuY && mouseY < menuY + presets.size() * ITEM_HEIGHT;
        if (!isMouseOver(mouseX, mouseY) && !overMenu) expanded = false;
    }

    private int menuY() {
        int height = presets.size() * ITEM_HEIGHT;
        int above = getY() - height - 3;
        if (above >= viewportTop) return above;
        return Math.min(viewportBottom - height, getBottom() + 3);
    }

    private String fitted(String text, int maximumWidth) {
        var font = DeckFonts.ui();
        if (font.width(text) <= maximumWidth) return text;
        return font.plainSubstrByWidth(text, Math.max(0, maximumWidth - font.width("…"))) + "…";
    }

    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
