package com.yoima.moddeck.api;

import com.yoima.moddeck.api.option.ConfigOption;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

/** Composable condition used to enable or display entries from current draft values. */
@FunctionalInterface
public interface ConfigRequirement {
    boolean test();

    @SafeVarargs static <T> ConfigRequirement isValue(ConfigOption<T> option, T first, T... remaining) {
        Objects.requireNonNull(option, "option");
        Set<T> values = new java.util.HashSet<>();
        values.add(first);
        values.addAll(Arrays.asList(remaining));
        return () -> values.contains(option.draftValue());
    }

    static <T> ConfigRequirement matches(ConfigOption<T> first, ConfigOption<T> second) {
        return () -> Objects.equals(first.draftValue(), second.draftValue());
    }

    static ConfigRequirement isTrue(ConfigOption<Boolean> option) {
        return () -> Boolean.TRUE.equals(option.draftValue());
    }

    static ConfigRequirement isFalse(ConfigOption<Boolean> option) {
        return () -> Boolean.FALSE.equals(option.draftValue());
    }

    static ConfigRequirement not(ConfigRequirement requirement) {
        return () -> !requirement.test();
    }

    static ConfigRequirement all(ConfigRequirement... requirements) {
        return () -> Arrays.stream(requirements).allMatch(ConfigRequirement::test);
    }

    static ConfigRequirement any(ConfigRequirement... requirements) {
        return () -> Arrays.stream(requirements).anyMatch(ConfigRequirement::test);
    }

    static ConfigRequirement none(ConfigRequirement... requirements) {
        return () -> Arrays.stream(requirements).noneMatch(ConfigRequirement::test);
    }

    static ConfigRequirement one(ConfigRequirement... requirements) {
        return () -> Arrays.stream(requirements).filter(ConfigRequirement::test).limit(2).count() == 1;
    }
}
