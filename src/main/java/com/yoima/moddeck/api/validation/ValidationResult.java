package com.yoima.moddeck.api.validation;

import com.yoima.moddeck.api.ConfigText;
import java.util.Objects;
import java.util.Optional;

public record ValidationResult(boolean valid, Optional<ConfigText> error) {
    public ValidationResult {
        error = Objects.requireNonNull(error, "error");
        if (valid && error.isPresent()) throw new IllegalArgumentException("A valid result cannot contain an error");
        if (!valid && error.isEmpty()) throw new IllegalArgumentException("An invalid result requires an error");
    }

    public static ValidationResult success() { return new ValidationResult(true, Optional.empty()); }
    public static ValidationResult invalid(ConfigText error) {
        return new ValidationResult(false, Optional.of(Objects.requireNonNull(error, "error")));
    }
}
