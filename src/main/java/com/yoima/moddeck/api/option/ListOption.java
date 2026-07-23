package com.yoima.moddeck.api.option;

import com.yoima.moddeck.api.ConfigText;
import com.yoima.moddeck.api.OptionPresentation;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ListOption<T> extends ConfigOption<List<T>> {
    private final ValueCodec<T> elementCodec;
    private final int minimumSize;
    private final int maximumSize;

    public ListOption(String id, ConfigText name, ConfigText description, List<T> defaultValue,
                      ValueCodec<T> elementCodec, int minimumSize, int maximumSize) {
        super(id, name, description, List.copyOf(defaultValue), OptionPresentation.LIST);
        if (minimumSize < 0 || maximumSize < minimumSize || defaultValue.size() < minimumSize
                || defaultValue.size() > maximumSize) {
            throw new IllegalArgumentException("Invalid list size range for " + id);
        }
        this.elementCodec = Objects.requireNonNull(elementCodec, "elementCodec");
        this.minimumSize = minimumSize;
        this.maximumSize = maximumSize;
    }

    public int minimumSize() { return minimumSize; }
    public int maximumSize() { return maximumSize; }
    public String encodeElement(T value) { return elementCodec.encode(value); }
    public T decodeElement(String value) { return elementCodec.decode(value); }

    @Override public List<T> decode(Object value) {
        if (!(value instanceof List<?> values)) throw new IllegalArgumentException("Expected list for " + id());
        List<T> decoded = new ArrayList<>(values.size());
        for (Object element : values) {
            if (!(element instanceof String text)) throw new IllegalArgumentException("Expected string list element for " + id());
            decoded.add(elementCodec.decode(text));
        }
        return validate(decoded);
    }

    @Override public Object encode() { return value().stream().map(elementCodec::encode).toList(); }

    @Override protected List<T> validate(List<T> value) {
        List<T> copy = List.copyOf(value);
        if (copy.size() < minimumSize || copy.size() > maximumSize) {
            throw new IllegalArgumentException("List size is out of range for " + id());
        }
        return copy;
    }
}
