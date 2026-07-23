package com.yoima.moddeck.api.autoconfig;

import com.yoima.moddeck.api.ConfigDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class AutoConfigHolder<T> {
    private final T config;
    private final ConfigDefinition definition;
    private final List<Consumer<T>> loadListeners;
    private final List<Consumer<T>> saveListeners;

    AutoConfigHolder(T config, ConfigDefinition definition, List<Consumer<T>> loadListeners,
                     List<Consumer<T>> saveListeners) {
        this.config = config;
        this.definition = definition;
        this.loadListeners = loadListeners;
        this.saveListeners = saveListeners;
    }

    public T config() { return config; }
    public ConfigDefinition definition() { return definition; }
    public AutoConfigHolder<T> onLoad(Consumer<T> listener) {
        loadListeners.add(listener);
        listener.accept(config);
        return this;
    }
    public AutoConfigHolder<T> onSave(Consumer<T> listener) { saveListeners.add(listener); return this; }
}
