package com.yoima.moddeck.client.widget;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Shared interaction contract for widgets that render a popup outside their row. */
public interface ExpandableOptionWidget {
    boolean handleExpandedClick(double mouseX, double mouseY);
    void collapseIfOutside(double mouseX, double mouseY);
    void setPopupViewport(int top, int bottom);
    boolean isExpanded();
    void extractPopupRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta);
}
