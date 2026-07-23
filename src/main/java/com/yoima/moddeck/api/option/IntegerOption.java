package com.yoima.moddeck.api.option;

import com.yoima.moddeck.api.OptionPresentation;
import com.yoima.moddeck.api.ConfigText;

public final class IntegerOption extends ConfigOption<Integer> {
    private final int minimum;
    private final int maximum;
    private final int step;

    public IntegerOption(String id, String name, String description, int defaultValue,
                         int minimum, int maximum, int step) {
        super(id, name, description, defaultValue, OptionPresentation.SLIDER);
        if (minimum > maximum || step <= 0 || defaultValue < minimum || defaultValue > maximum) {
            throw new IllegalArgumentException("Invalid integer range for " + id);
        }
        this.minimum = minimum;
        this.maximum = maximum;
        this.step = step;
    }

    public IntegerOption(String id, ConfigText name, ConfigText description, int defaultValue,
                         int minimum, int maximum, int step) {
        super(id, name, description, defaultValue, OptionPresentation.SLIDER);
        if (minimum > maximum || step <= 0 || defaultValue < minimum || defaultValue > maximum) {
            throw new IllegalArgumentException("Invalid integer range for " + id);
        }
        this.minimum = minimum;
        this.maximum = maximum;
        this.step = step;
    }

    public int minimum() { return minimum; }
    public int maximum() { return maximum; }
    public int step() { return step; }

    @Override public Integer decode(Object value) {
        if (value instanceof Number number) return validate(number.intValue());
        if (value instanceof String text) return validate(Integer.parseInt(text));
        throw new IllegalArgumentException("Expected integer for " + id());
    }

    @Override protected Integer validate(Integer value) {
        if (value < minimum || value > maximum) throw new IllegalArgumentException("Out of range: " + value);
        int snapped = minimum + Math.round((value - minimum) / (float) step) * step;
        return Math.min(maximum, snapped);
    }
}
