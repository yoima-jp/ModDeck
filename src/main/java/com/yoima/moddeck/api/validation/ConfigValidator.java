package com.yoima.moddeck.api.validation;

@FunctionalInterface
public interface ConfigValidator<T> {
    ValidationResult validate(T value);

    static <T> ConfigValidator<T> acceptingAll() { return value -> ValidationResult.success(); }
}
