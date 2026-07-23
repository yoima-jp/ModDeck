package com.yoima.moddeck.client.widget;

import com.yoima.moddeck.api.option.*;
import java.util.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;

/** Central renderer mapping keeps API option types independent from Minecraft widgets. */
public final class OptionWidgetRegistry {
    @FunctionalInterface
    public interface Factory<T extends ConfigOption<?>> {
        AbstractWidget create(Font font, int x, int y, int width, T option, Runnable onChanged, boolean opensUp);
    }

    private static final Map<Class<?>, Factory<?>> FACTORIES = new HashMap<>();

    static {
        register(BooleanOption.class, (font, x, y, width, option, changed, up) ->
                new SwitchWidget(x + width - 34, y, option, changed));
        register(IntegerOption.class, (font, x, y, width, option, changed, up) ->
                new SliderWidget(x, y, width, option, changed));
        register(DoubleOption.class, (font, x, y, width, option, changed, up) ->
                new SliderWidget(x, y, width, option, changed));
        register(LongOption.class, (font, x, y, width, option, changed, up) ->
                new SliderWidget(x, y, width, option, changed));
        register(FloatOption.class, (font, x, y, width, option, changed, up) ->
                new SliderWidget(x, y, width, option, changed));
        register(StringOption.class, (font, x, y, width, option, changed, up) ->
                new TextFieldWidget(font, x, y, width, option, changed));
        FACTORIES.put(EnumOption.class, (Factory<EnumOption<?>>) (font, x, y, width, option, changed, up) ->
                new EnumSelectorWidget(x, y, width, option, changed, up));
        FACTORIES.put(SelectorOption.class, (Factory<SelectorOption<?>>) (font, x, y, width, option, changed, up) ->
                new SelectorWidget<>(x, y, width, option, changed, up));
        register(ColorOption.class, (font, x, y, width, option, changed, up) ->
                new ColorFieldWidget(font, x, y, width, option, changed));
        register(KeybindOption.class, (font, x, y, width, option, changed, up) ->
                new KeybindWidget(x, y, width, option, changed));
        FACTORIES.put(ListOption.class, (Factory<ListOption<?>>) (font, x, y, width, option, changed, up) ->
                new ListFieldWidget<>(font, x, y, width, option, changed));
        register(SubcategoryOption.class, (font, x, y, width, option, changed, up) ->
                new SubcategoryWidget(x, y, width, option, changed));
    }

    private OptionWidgetRegistry() {}

    public static AbstractWidget create(Font font, int x, int y, int width, ConfigOption<?> option,
                                        Runnable onChanged, boolean opensUp) {
        Factory<?> factory = FACTORIES.get(option.getClass());
        if (factory == null) throw new IllegalArgumentException("No widget factory for " + option.getClass().getName());
        return createUnchecked(factory, font, x, y, width, option, onChanged, opensUp);
    }

    /** Registers or replaces a widget factory for a custom option class. */
    public static synchronized <T extends ConfigOption<?>> void register(Class<T> type, Factory<T> factory) {
        FACTORIES.put(Objects.requireNonNull(type, "type"), Objects.requireNonNull(factory, "factory"));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static AbstractWidget createUnchecked(Factory factory, Font font, int x, int y, int width,
                                                   ConfigOption<?> option, Runnable onChanged, boolean opensUp) {
        return factory.create(font, x, y, width, option, onChanged, opensUp);
    }
}
