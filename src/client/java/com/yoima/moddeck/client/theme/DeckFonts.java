package com.yoima.moddeck.client.theme;

import com.yoima.moddeck.client.mixin.FontManagerAccessor;
import com.yoima.moddeck.client.mixin.MinecraftAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

/** Provides the bundled language-appropriate Noto Sans font with Minecraft's glyph fallbacks. */
public final class DeckFonts {
    private static final Identifier UI_FONT = Identifier.fromNamespaceAndPath("moddeck", "ui");
    private static final Identifier KOREAN_UI_FONT = Identifier.fromNamespaceAndPath("moddeck", "ui_ko");
    private static final Identifier SIMPLIFIED_CHINESE_UI_FONT = Identifier.fromNamespaceAndPath("moddeck", "ui_zh_cn");
    private static final Identifier TRADITIONAL_CHINESE_UI_FONT = Identifier.fromNamespaceAndPath("moddeck", "ui_zh_tw");
    private static final Font UI = new Font(new Font.Provider() {
        @Override public net.minecraft.client.gui.GlyphSource glyphs(FontDescription ignored) {
            return fontSet().source(false);
        }

        @Override public net.minecraft.client.gui.font.glyphs.EffectGlyph effect() {
            return fontSet().whiteGlyph();
        }

        private FontSet fontSet() {
            // Font sets are rebuilt on resource reload. Resolve the current set lazily rather
            // than caching it so F3+T and resource-pack changes cannot leave stale GPU glyphs.
            // CJK characters share code points but need region-specific glyph forms, so select
            // the matching font set instead of relying on a visually inconsistent fallback.
            Minecraft minecraft = Minecraft.getInstance();
            var manager = ((MinecraftAccessor) minecraft).moddeck$getFontManager();
            return ((FontManagerAccessor) manager).moddeck$getFontSet(
                    fontForLanguage(minecraft.getLanguageManager().getSelected()));
        }
    });

    private DeckFonts() {}

    public static Font ui() {
        return UI;
    }

    private static Identifier fontForLanguage(String languageCode) {
        return switch (languageCode) {
            case "ko_kr" -> KOREAN_UI_FONT;
            case "zh_cn" -> SIMPLIFIED_CHINESE_UI_FONT;
            case "zh_tw" -> TRADITIONAL_CHINESE_UI_FONT;
            default -> UI_FONT;
        };
    }
}
