package com.yoima.moddeck.api.option;

import com.yoima.moddeck.api.ConfigText;
import com.yoima.moddeck.api.OptionPresentation;

public final class LongOption extends ConfigOption<Long> {
    private final long minimum;
    private final long maximum;
    private final long step;

    public LongOption(String id, ConfigText name, ConfigText description, long defaultValue,
                      long minimum, long maximum, long step) {
        super(id, name, description, defaultValue, OptionPresentation.SLIDER);
        if (minimum > maximum || step <= 0 || defaultValue < minimum || defaultValue > maximum) {
            throw new IllegalArgumentException("Invalid long range for " + id);
        }
        this.minimum = minimum;
        this.maximum = maximum;
        this.step = step;
    }

    public long minimum() { return minimum; }
    public long maximum() { return maximum; }
    public long step() { return step; }

    @Override public Long decode(Object value) {
        if (value instanceof Number number) return validate(number.longValue());
        if (value instanceof String text) return validate(Long.parseLong(text));
        throw new IllegalArgumentException("Expected long for " + id());
    }

    @Override protected Long validate(Long value) {
        if (value < minimum || value > maximum) throw new IllegalArgumentException("Out of range: " + value);
        long offset = value - minimum;
        long snapped = minimum + Math.round(offset / (double) step) * step;
        return Math.min(maximum, snapped);
    }
}
