package com.yoima.moddeck.client.theme;

import com.yoima.moddeck.client.mixin.FontManagerAccessor;
import com.yoima.moddeck.client.mixin.MinecraftAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

/** Provides the bundled Noto Sans JP font without replacing Minecraft's global UI font. */
public final class DeckFonts {
    private static final Identifier UI_FONT = Identifier.fromNamespaceAndPath("moddeck", "ui");
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
            Minecraft minecraft = Minecraft.getInstance();
            var manager = ((MinecraftAccessor) minecraft).moddeck$getFontManager();
            return ((FontManagerAccessor) manager).moddeck$getFontSet(UI_FONT);
        }
    });

    private DeckFonts() {}

    public static Font ui() {
        return UI;
    }
}
