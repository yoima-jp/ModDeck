package com.yoima.moddeck.api.option;

import com.yoima.moddeck.api.ConfigText;
import com.yoima.moddeck.api.OptionPresentation;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public final class SelectorOption<T> extends ConfigOption<T> {
    private final List<T> choices;
    private final ValueCodec<T> codec;
    private final Function<T, ConfigText> labelFactory;

    public SelectorOption(String id, ConfigText name, ConfigText description, T defaultValue,
                          List<T> choices, ValueCodec<T> codec, Function<T, ConfigText> labelFactory) {
        super(id, name, description, defaultValue, OptionPresentation.DROPDOWN);
        this.choices = List.copyOf(choices);
        this.codec = Objects.requireNonNull(codec, "codec");
        this.labelFactory = Objects.requireNonNull(labelFactory, "labelFactory");
        if (this.choices.isEmpty() || !this.choices.contains(defaultValue)) {
            throw new IllegalArgumentException("Selector choices must contain the default for " + id);
        }
    }

    public List<T> choices() { return choices; }
    public ConfigText label(T value) { return Objects.requireNonNull(labelFactory.apply(value), "label"); }
    public String encode(T value) { return codec.encode(value); }

    @Override public T decode(Object value) {
        if (!(value instanceof String text)) throw new IllegalArgumentException("Expected selector string for " + id());
        return validate(codec.decode(text));
    }

    @Override public Object encode() { return codec.encode(value()); }

    @Override protected T validate(T value) {
        if (!choices.contains(value)) throw new IllegalArgumentException("Unknown selector value for " + id());
        return value;
    }
}
