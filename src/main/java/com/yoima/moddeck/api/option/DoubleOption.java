package com.yoima.moddeck.api.option;

import com.yoima.moddeck.api.OptionPresentation;
import java.math.BigDecimal;

public final class DoubleOption extends ConfigOption<Double> {
    private final double minimum;
    private final double maximum;
    private final double step;

    public DoubleOption(String id, String name, String description, double defaultValue,
                        double minimum, double maximum, double step) {
        super(id, name, description, defaultValue, OptionPresentation.SLIDER);
        if (!Double.isFinite(minimum) || !Double.isFinite(maximum) || !Double.isFinite(step)
                || minimum > maximum || step <= 0 || defaultValue < minimum || defaultValue > maximum) {
            throw new IllegalArgumentException("Invalid double range for " + id);
        }
        this.minimum = minimum;
        this.maximum = maximum;
        this.step = step;
    }

    public double minimum() { return minimum; }
    public double maximum() { return maximum; }
    public double step() { return step; }

    @Override public Double decode(Object value) {
        if (value instanceof Number number) return validate(number.doubleValue());
        if (value instanceof String text) return validate(Double.parseDouble(text));
        throw new IllegalArgumentException("Expected number for " + id());
    }

    @Override protected Double validate(Double value) {
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            throw new IllegalArgumentException("Out of range: " + value);
        }
        // Decimal configuration steps should remain human-readable (0.3 rather than the binary
        // floating-point artifact 0.30000000000000004) in widgets and persisted JSON.
        double snapped = BigDecimal.valueOf(minimum)
                .add(BigDecimal.valueOf(step).multiply(BigDecimal.valueOf(Math.round((value - minimum) / step))))
                .doubleValue();
        return Math.min(maximum, Math.max(minimum, snapped));
    }
}
