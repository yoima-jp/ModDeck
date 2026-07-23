package com.yoima.moddeck.api.option;

import com.yoima.moddeck.api.ConfigText;
import com.yoima.moddeck.api.OptionPresentation;

/** Non-persistent explanatory row rendered using the current Mod Deck typography. */
public final class DescriptionOption extends ConfigOption<String> {
    public DescriptionOption(String id, ConfigText text) {
        super(id, text, ConfigText.empty(), "", OptionPresentation.DESCRIPTION);
        editable(false);
    }

    @Override public String decode(Object value) { return ""; }
    @Override public boolean persistent() { return false; }
    @Override public boolean isDirty() { return false; }
    @Override public void reset() {}
}
