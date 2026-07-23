package com.yoima.moddeck.api.option;

import com.yoima.moddeck.api.ConfigText;
import com.yoima.moddeck.api.OptionPresentation;

/** RGB or ARGB color stored as an integer without depending on a rendering library. */
public final class ColorOption extends ConfigOption<Integer> {
    private final boolean alpha;

    public ColorOption(String id, ConfigText name, ConfigText description, int defaultValue, boolean alpha) {
        super(id, name, description, normalize(defaultValue, alpha), OptionPresentation.COLOR);
        this.alpha = alpha;
    }

    public boolean alpha() { return alpha; }

    public String hexValue() { return String.format(alpha ? "%08X" : "%06X", value()); }
    public String draftHexValue() { return String.format(alpha ? "%08X" : "%06X", draftValue()); }

    @Override public Integer decode(Object value) {
        if (value instanceof Number number) return normalize(number.intValue(), alpha);
        if (value instanceof String text) {
            String hex = text.startsWith("#") ? text.substring(1) : text;
            int expected = alpha ? 8 : 6;
            if (!hex.matches("[0-9a-fA-F]{" + expected + "}")) {
                throw new IllegalArgumentException("Expected " + expected + " hexadecimal digits for " + id());
            }
            return (int) Long.parseLong(hex, 16);
        }
        throw new IllegalArgumentException("Expected color for " + id());
    }

    private static int normalize(int value, boolean alpha) { return alpha ? value : value & 0xFFFFFF; }
}
