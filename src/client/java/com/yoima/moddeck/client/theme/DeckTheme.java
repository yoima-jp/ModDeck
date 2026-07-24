package com.yoima.moddeck.client.theme;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Shared visual tokens derived from the Mod Deck reference design. */
public final class DeckTheme {
    private static final Identifier ROUNDED_SMALL = Identifier.fromNamespaceAndPath("moddeck", "shapes/rounded_small");
    private static final Identifier ROUNDED_MEDIUM = Identifier.fromNamespaceAndPath("moddeck", "shapes/rounded_medium");
    private static final Identifier ROUNDED_LARGE = Identifier.fromNamespaceAndPath("moddeck", "shapes/rounded_large");
    public enum Mode {
        AUTO("moddeck.theme.auto"), LIGHT("moddeck.theme.light"), DARK("moddeck.theme.dark");

        private final String translationKey;

        Mode(String translationKey) {
            this.translationKey = translationKey;
        }

        public String translationKey() {
            return translationKey;
        }
    }

    public static int BACKGROUND;
    public static int BACKGROUND_TOP;
    public static int PANEL;
    public static int PANEL_RAISED;
    public static int CARD;
    public static int FIELD;
    public static int FIELD_HOVER;
    public static int DIVIDER;
    public static int ACCENT;
    public static int ACCENT_DARK;
    public static int ACCENT_MUTED;
    public static int TEXT;
    public static int TEXT_SECONDARY;
    public static int TEXT_MUTED;
    public static int SUCCESS;
    private static Mode mode = Mode.AUTO;
    private static boolean dark = true;

    static {
        applyMode();
    }

    private DeckTheme() {}

    public static boolean isDark() {
        return dark;
    }

    public static Mode mode() {
        return mode;
    }

    public static void setMode(Mode nextMode) {
        mode = java.util.Objects.requireNonNull(nextMode, "nextMode");
        applyMode();
    }

    /** Applies a screen-local accent while deriving readable dark and muted variants. */
    public static void applyAccent(int color) {
        if (color == 0) return;
        ACCENT = 0xFF000000 | color & 0xFFFFFF;
        ACCENT_DARK = blend(ACCENT, 0xFF000000, 0.28f);
        ACCENT_MUTED = blend(PANEL, ACCENT, dark ? 0.38f : 0.22f);
    }

    private static int blend(int from, int to, float amount) {
        int red = Math.round(((from >> 16) & 0xFF) * (1 - amount) + ((to >> 16) & 0xFF) * amount);
        int green = Math.round(((from >> 8) & 0xFF) * (1 - amount) + ((to >> 8) & 0xFF) * amount);
        int blue = Math.round((from & 0xFF) * (1 - amount) + (to & 0xFF) * amount);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static void applyMode() {
        boolean useDark = mode == Mode.DARK || mode == Mode.AUTO && SystemThemeDetector.prefersDark();
        if (useDark) applyDark(); else applyLight();
    }

    private static void applyDark() {
        dark = true;
        BACKGROUND = 0xFF0C1018;
        BACKGROUND_TOP = 0xFF10141D;
        PANEL = 0xFF171C27;
        PANEL_RAISED = 0xFF1D2330;
        CARD = 0xFF202633;
        FIELD = 0xFF2A3040;
        FIELD_HOVER = 0xFF333A4C;
        DIVIDER = 0xFF303746;
        ACCENT = 0xFF8B6FE8;
        ACCENT_DARK = 0xFF5E4A9E;
        ACCENT_MUTED = 0xFF4D426F;
        TEXT = 0xFFF2F2F7;
        TEXT_SECONDARY = 0xFFB1B6C5;
        TEXT_MUTED = 0xFF858C9D;
        SUCCESS = 0xFF8ED7A6;
    }

    private static void applyLight() {
        dark = false;
        BACKGROUND = 0xFFE9ECF3;
        BACKGROUND_TOP = 0xFFF5F6FA;
        PANEL = 0xFFF8F9FC;
        PANEL_RAISED = 0xFFE9EDF5;
        CARD = 0xFFF1F3F8;
        FIELD = 0xFFE1E5EE;
        FIELD_HOVER = 0xFFD6DBE7;
        DIVIDER = 0xFFC8CFDC;
        ACCENT = 0xFF7659DE;
        ACCENT_DARK = 0xFF6548C5;
        ACCENT_MUTED = 0xFFD8CEF6;
        TEXT = 0xFF202431;
        TEXT_SECONDARY = 0xFF596174;
        TEXT_MUTED = 0xFF7D8598;
        SUCCESS = 0xFF287A45;
    }

    /** Uses anti-aliased nine-slice sprites so corners stay smooth at every control size. */
    public static void roundedRect(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                                   int radius, int color) {
        if (width <= 0 || height <= 0) return;
        int availableRadius = Math.min(width, height) / 2;
        if (radius <= 1 || availableRadius < 4) {
            graphics.fill(x, y, x + width, y + height, color);
            return;
        }
        Identifier sprite;
        if (radius >= 7 && availableRadius >= 8) {
            sprite = ROUNDED_LARGE;
        } else if (radius >= 5 && availableRadius >= 6) {
            sprite = ROUNDED_MEDIUM;
        } else {
            sprite = ROUNDED_SMALL;
        }
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, width, height, color);
    }

    public static void border(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                              int radius, int borderColor, int fillColor) {
        roundedRect(graphics, x, y, width, height, radius, borderColor);
        roundedRect(graphics, x + 1, y + 1, width - 2, height - 2, Math.max(1, radius - 1), fillColor);
    }

    public static void logo(GuiGraphicsExtractor graphics, int x, int y, int size) {
        roundedRect(graphics, x, y, size, size, 5, ACCENT);
        roundedRect(graphics, x + 4, y + 4, size - 8, size - 8, 3, ACCENT_DARK);
        roundedRect(graphics, x + 7, y + 7, size - 14, size - 14, 2, TEXT);
        roundedRect(graphics, x + 10, y + 10, size - 20, size - 20, 1, PANEL);
    }

    public static void modCube(GuiGraphicsExtractor graphics, int x, int y, int size) {
        int grassHeight = Math.max(4, size / 4);
        roundedRect(graphics, x, y, size, size, 3, 0xFF795336);
        graphics.fill(x + 1, y + grassHeight, x + size - 1, y + size - 1, 0xFF795336);
        graphics.fill(x + 1, y + 1, x + size - 1, y + grassHeight + 1, 0xFF6DA653);
        graphics.fill(x + size / 2, y + grassHeight + 2, x + size - 2, y + size - 2, 0xFF68452E);
        graphics.fill(x + 3, y + grassHeight + 5, x + 6, y + grassHeight + 8, 0xFF9B7047);
    }

    /** Centers text without Minecraft's default drop shadow; shadows become distracting in light mode. */
    public static void centeredText(GuiGraphicsExtractor graphics, Font font, String text,
                                    int centerX, int y, int color) {
        graphics.text(font, text, centerX - font.width(text) / 2, y, color, false);
    }

    /** Centers a component without Minecraft's default drop shadow. */
    public static void centeredText(GuiGraphicsExtractor graphics, Font font, Component text,
                                    int centerX, int y, int color) {
        FormattedCharSequence visual = text.getVisualOrderText();
        graphics.text(font, visual, centerX - font.width(visual) / 2, y, color, false);
    }
}
