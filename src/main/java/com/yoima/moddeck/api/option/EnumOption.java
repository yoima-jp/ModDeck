package com.yoima.moddeck.api.option;

import com.yoima.moddeck.api.OptionPresentation;
import com.yoima.moddeck.api.ConfigText;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public final class EnumOption<E extends Enum<E>> extends ConfigOption<E> {
    private final Class<E> enumType;
    private final List<E> values;
    private Function<E, ConfigText> labelFactory = value -> ConfigText.literal(value.name());

    public EnumOption(String id, String name, String description, E defaultValue, Class<E> enumType) {
        super(id, name, description, defaultValue, OptionPresentation.SELECTOR);
        this.enumType = enumType;
        this.values = List.of(enumType.getEnumConstants());
        if (!enumType.isInstance(defaultValue) || values.isEmpty()) {
            throw new IllegalArgumentException("Invalid enum for " + id);
        }
    }

    public EnumOption(String id, ConfigText name, ConfigText description, E defaultValue, Class<E> enumType) {
        super(id, name, description, defaultValue, OptionPresentation.SELECTOR);
        this.enumType = enumType;
        this.values = List.of(enumType.getEnumConstants());
        if (!enumType.isInstance(defaultValue) || values.isEmpty()) {
            throw new IllegalArgumentException("Invalid enum for " + id);
        }
    }

    public Class<E> enumType() { return enumType; }
    public List<E> values() { return values; }
    public ConfigText label(E value) { return Objects.requireNonNull(labelFactory.apply(value), "label"); }
    public EnumOption<E> labels(Function<E, ConfigText> factory) {
        this.labelFactory = Objects.requireNonNull(factory, "factory");
        return this;
    }

    public E next() {
        int index = (values.indexOf(value()) + 1) % values.size();
        E next = values.get(index);
        setValue(next);
        return next;
    }

    @Override public E decode(Object value) {
        if (!(value instanceof String text)) throw new IllegalArgumentException("Expected enum name for " + id());
        return Enum.valueOf(enumType, text);
    }

    @Override public Object encode() { return value().name(); }
    @Override public Object encodeDraft() { return draftValue().name(); }
}
