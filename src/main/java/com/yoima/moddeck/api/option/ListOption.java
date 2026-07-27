package com.yoima.moddeck.api.option;

import com.yoima.moddeck.api.ConfigText;
import com.yoima.moddeck.api.OptionPresentation;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import com.yoima.moddeck.api.validation.ConfigValidator;
import com.yoima.moddeck.api.validation.ValidationResult;

public final class ListOption<T> extends ConfigOption<List<T>> {
    private final ValueCodec<T> elementCodec;
    private final int minimumSize;
    private final int maximumSize;
    private Supplier<T> newElementSupplier;
    private ConfigValidator<T> elementValidator = ConfigValidator.acceptingAll();
    private boolean insertionAllowed = true;
    private boolean deletionAllowed = true;
    private boolean reorderingAllowed = true;

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
    public ValidationResult validateElement(T value) {
        return Objects.requireNonNull(elementValidator.validate(value), "element validation result");
    }

    /**
     * Returns the text with which a new row should begin. An empty string is intentional when
     * no supplier was configured: editors can show a blank row before the codec can produce a
     * typed value (for example, before a number has been entered).
     */
    public String newElementText() {
        return newElementSupplier == null ? "" : encodeElement(requireNewElement());
    }

    public T newElement() {
        if (newElementSupplier != null) return requireNewElement();
        return Objects.requireNonNull(decodeElement(""), "decoded blank element");
    }
    public boolean insertionAllowed() { return insertionAllowed; }
    public boolean deletionAllowed() { return deletionAllowed; }
    public boolean reorderingAllowed() { return reorderingAllowed; }
    public ListOption<T> newElementFrom(Supplier<T> supplier) {
        newElementSupplier = Objects.requireNonNull(supplier, "supplier");
        return this;
    }

    private T requireNewElement() {
        return Objects.requireNonNull(newElementSupplier.get(), "new element supplier result");
    }
    public ListOption<T> validateElementsWith(ConfigValidator<T> validator) {
        elementValidator = Objects.requireNonNull(validator, "validator");
        validate(draftValue());
        return this;
    }
    public ListOption<T> allowInsertion(boolean allowed) { insertionAllowed = allowed; return this; }
    public ListOption<T> allowDeletion(boolean allowed) { deletionAllowed = allowed; return this; }
    public ListOption<T> allowReordering(boolean allowed) { reorderingAllowed = allowed; return this; }

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
    @Override public Object encodeDraft() { return draftValue().stream().map(elementCodec::encode).toList(); }

    @Override protected List<T> validate(List<T> value) {
        List<T> copy = List.copyOf(value);
        if (copy.size() < minimumSize || copy.size() > maximumSize) {
            throw new IllegalArgumentException("List size is out of range for " + id());
        }
        for (T element : copy) {
            ValidationResult result = validateElement(element);
            if (!result.valid()) throw new IllegalArgumentException(result.error().orElseThrow().component().getString());
        }
        return copy;
    }
}
