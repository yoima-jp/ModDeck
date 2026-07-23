package com.yoima.moddeck.api.option;

import com.yoima.moddeck.api.ConfigText;
import com.yoima.moddeck.api.OptionPresentation;
import java.math.BigDecimal;

public final class FloatOption extends ConfigOption<Float> {
    private final float minimum;
    private final float maximum;
    private final float step;

    public FloatOption(String id, ConfigText name, ConfigText description, float defaultValue,
                       float minimum, float maximum, float step) {
        super(id, name, description, defaultValue, OptionPresentation.SLIDER);
        if (!Float.isFinite(minimum) || !Float.isFinite(maximum) || !Float.isFinite(step)
                || minimum > maximum || step <= 0 || defaultValue < minimum || defaultValue > maximum) {
            throw new IllegalArgumentException("Invalid float range for " + id);
        }
        this.minimum = minimum;
        this.maximum = maximum;
        this.step = step;
    }

    public float minimum() { return minimum; }
    public float maximum() { return maximum; }
    public float step() { return step; }

    @Override public Float decode(Object value) {
        if (value instanceof Number number) return validate(number.floatValue());
        if (value instanceof String text) return validate(Float.parseFloat(text));
        throw new IllegalArgumentException("Expected float for " + id());
    }

    @Override protected Float validate(Float value) {
        if (!Float.isFinite(value) || value < minimum || value > maximum) {
            throw new IllegalArgumentException("Out of range: " + value);
        }
        // Float.toString emits the shortest decimal that round-trips to the same float. Building
        // BigDecimal from that text avoids preserving the widened binary artifacts of 0.1f.
        float snapped = new BigDecimal(Float.toString(minimum))
                .add(new BigDecimal(Float.toString(step))
                        .multiply(BigDecimal.valueOf(Math.round((value - minimum) / step))))
                .floatValue();
        return Math.min(maximum, Math.max(minimum, snapped));
    }
}
