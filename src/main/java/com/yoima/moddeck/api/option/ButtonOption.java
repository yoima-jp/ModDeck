package com.yoima.moddeck.api.option;

import com.yoima.moddeck.api.ConfigText;
import com.yoima.moddeck.api.OptionPresentation;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Non-persistent entry that invokes a mod-provided action from the generated config screen. */
public final class ButtonOption extends ConfigOption<Boolean> {
    private static final Logger LOGGER = Logger.getLogger(ButtonOption.class.getName());
    private final ConfigText buttonText;
    private final Runnable action;

    public ButtonOption(String id, ConfigText name, ConfigText description,
                        ConfigText buttonText, Runnable action) {
        super(id, name, description, false, OptionPresentation.BUTTON);
        this.buttonText = Objects.requireNonNull(buttonText, "buttonText");
        this.action = Objects.requireNonNull(action, "action");
    }

    public ConfigText buttonText() { return buttonText; }

    /** Executes the action without allowing a mod callback failure to close the config screen. */
    public boolean runAction() {
        try {
            action.run();
            return true;
        } catch (RuntimeException exception) {
            LOGGER.log(Level.SEVERE, "Config button action failed for " + id(), exception);
            return false;
        }
    }

    @Override public Boolean decode(Object value) { return false; }
    @Override public boolean persistent() { return false; }
    @Override public boolean isDirty() { return false; }
    @Override public void reset() {}
    @Override public void notifySaved() {}
}
