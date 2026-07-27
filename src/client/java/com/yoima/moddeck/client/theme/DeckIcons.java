package com.yoima.moddeck.client.theme;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Lucide SVG icons rendered through 64px textures because Minecraft does not decode SVGs. */
public final class DeckIcons {
    public enum Icon {
        CHECK("check"),
        CHEVRON_DOWN("chevron-down"),
        CHEVRON_RIGHT("chevron-right"),
        CHEVRON_UP("chevron-up"),
        CHEVRON_LEFT("chevron-left"),
        CLOSE("x"),
        GRIP_VERTICAL("grip-vertical"),
        HOME("house"),
        KEYBOARD("keyboard"),
        MONITOR("monitor"),
        MOON("moon"),
        RESET("rotate-ccw"),
        SAVE("save"),
        SEARCH("search"),
        SETTINGS("settings"),
        SUN("sun");

        private final Identifier texture;

        Icon(String name) {
            texture = Identifier.fromNamespaceAndPath("moddeck", "textures/gui/icons/" + name + ".png");
        }
    }

    private DeckIcons() {}

    public static void draw(GuiGraphicsExtractor graphics, Icon icon, int x, int y, int size, int color) {
        // Destination size and source region are separate in this overload. Always sample the
        // complete 64px raster generated from the SVG, then scale it to the requested UI size.
        graphics.blit(RenderPipelines.GUI_TEXTURED, icon.texture, x, y, 0, 0,
                size, size, 64, 64, 64, 64, color);
    }
}
