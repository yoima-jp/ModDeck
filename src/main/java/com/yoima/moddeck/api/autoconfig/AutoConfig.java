package com.yoima.moddeck.api.autoconfig;

import com.yoima.moddeck.api.*;
import com.yoima.moddeck.api.option.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Consumer;

/** Reflection builder covering the common Cloth Auto Config workflow with Mod Deck JSON storage. */
public final class AutoConfig {
    private static final ValueCodec<String> STRING_CODEC = new ValueCodec<>() {
        @Override public String encode(String value) { return value; }
        @Override public String decode(String value) { return value; }
    };

    private AutoConfig() {}

    public static <T> AutoConfigHolder<T> register(T config) {
        Objects.requireNonNull(config, "config");
        Class<?> type = config.getClass();
        ModDeckAutoConfig metadata = type.getAnnotation(ModDeckAutoConfig.class);
        if (metadata == null) throw new IllegalArgumentException(type.getName() + " needs @ModDeckAutoConfig");

        List<Consumer<T>> loadListeners = new ArrayList<>();
        List<Consumer<T>> saveListeners = new ArrayList<>();
        List<Binding<T>> bindings = fields(type).stream()
                .filter(field -> !field.isAnnotationPresent(AutoIgnore.class))
                .filter(field -> field.isAnnotationPresent(AutoEntry.class))
                .map(field -> binding(config, field)).toList();
        if (bindings.isEmpty()) throw new IllegalArgumentException("Auto config has no @AutoEntry fields");

        ConfigDefinition.Builder builder = ConfigDefinition.builder(metadata.modId()).titleKey(metadata.titleKey());
        if (!metadata.descriptionKey().isBlank()) builder.descriptionKey(metadata.descriptionKey());
        Map<String, List<Binding<T>>> categories = new LinkedHashMap<>();
        bindings.stream().sorted(Comparator.comparingInt(binding -> binding.entry.categoryOrder()))
                .forEach(binding -> categories.computeIfAbsent(binding.entry.category(), ignored -> new ArrayList<>()).add(binding));
        categories.forEach((category, entries) -> {
            AutoEntry first = entries.getFirst().entry;
            builder.category(category, ConfigText.translatable(first.categoryKey()), first.categoryOrder());
            entries.stream().sorted(Comparator.comparingInt(binding -> binding.entry.order()))
                    .forEach(binding -> builder.addOption(binding.option));
        });
        builder.onSave(() -> saveListeners.forEach(listener -> listener.accept(config)));
        ConfigDefinition definition = builder.build();
        ConfigScreenApi.register(definition);
        // Registration loads persisted values. Synchronize the backing object only after that load.
        bindings.forEach(Binding::writeAppliedValue);
        loadListeners.forEach(listener -> listener.accept(config));
        return new AutoConfigHolder<>(config, definition, loadListeners, saveListeners);
    }

    private static List<Field> fields(Class<?> type) {
        List<Field> result = new ArrayList<>();
        for (Class<?> current = type; current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) result.add(field);
            }
        }
        return result;
    }

    private static <T> Binding<T> binding(T config, Field field) {
        field.setAccessible(true);
        AutoEntry entry = field.getAnnotation(AutoEntry.class);
        ConfigText name = ConfigText.translatable(entry.nameKey());
        ConfigText description = entry.descriptionKey().isBlank() ? ConfigText.empty()
                : ConfigText.translatable(entry.descriptionKey());
        Object value;
        try { value = field.get(config); }
        catch (IllegalAccessException exception) { throw new IllegalArgumentException("Cannot read " + field, exception); }
        ConfigOption<?> option = createOption(field, value, name, description);
        if (entry.requiresRestart()) option.requiresRestart();
        setSaveConsumer(option, saved -> {
            try { field.set(config, saved); }
            catch (IllegalAccessException exception) { throw new IllegalStateException("Cannot write " + field, exception); }
        });
        return new Binding<>(config, field, entry, option);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ConfigOption<?> createOption(Field field, Object value, ConfigText name, ConfigText description) {
        String id = field.getName().replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
        AutoRange range = field.getAnnotation(AutoRange.class);
        Class<?> type = field.getType();
        if (type == boolean.class || type == Boolean.class) return new BooleanOption(id, name, description, (Boolean) value);
        if ((type == int.class || type == Integer.class) && field.isAnnotationPresent(AutoColor.class)) {
            return new ColorOption(id, name, description, (Integer) value, field.getAnnotation(AutoColor.class).alpha());
        }
        if (type == int.class || type == Integer.class) {
            AutoRange r = requireRange(field, range);
            return new IntegerOption(id, name, description, (Integer) value, (int) r.min(), (int) r.max(), (int) r.step());
        }
        if (type == long.class || type == Long.class) {
            AutoRange r = requireRange(field, range);
            return new LongOption(id, name, description, (Long) value, (long) r.min(), (long) r.max(), (long) r.step());
        }
        if (type == float.class || type == Float.class) {
            AutoRange r = requireRange(field, range);
            return new FloatOption(id, name, description, (Float) value, (float) r.min(), (float) r.max(), (float) r.step());
        }
        if (type == double.class || type == Double.class) {
            AutoRange r = requireRange(field, range);
            return new DoubleOption(id, name, description, (Double) value, r.min(), r.max(), r.step());
        }
        if (type == String.class && field.isAnnotationPresent(AutoKeybind.class)) {
            AutoKeybind keybind = field.getAnnotation(AutoKeybind.class);
            EnumSet<KeybindOption.InputType> inputs = EnumSet.noneOf(KeybindOption.InputType.class);
            if (keybind.keyboard()) inputs.add(KeybindOption.InputType.KEYBOARD);
            if (keybind.mouse()) inputs.add(KeybindOption.InputType.MOUSE);
            return new KeybindOption(id, name, description, (String) value, inputs, keybind.unbound())
                    .allowModifiers(keybind.modifiers());
        }
        if (type == String.class) return new StringOption(id, name, description, (String) value, 4096);
        if (type.isEnum()) return new EnumOption(id, name, description, (Enum) value, type.asSubclass(Enum.class));
        if (List.class.isAssignableFrom(type) && ((List<?>) value).stream().allMatch(String.class::isInstance)) {
            return new ListOption<>(id, name, description, (List<String>) value, STRING_CODEC, 0, Integer.MAX_VALUE)
                    .newElementFrom(() -> "");
        }
        throw new IllegalArgumentException("Unsupported auto config field type: " + field);
    }

    private static AutoRange requireRange(Field field, AutoRange range) {
        if (range == null) throw new IllegalArgumentException("Numeric field needs @AutoRange: " + field);
        return range;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setSaveConsumer(ConfigOption option, Consumer<Object> consumer) { option.onSaved(consumer); }

    private record Binding<T>(T config, Field field, AutoEntry entry, ConfigOption<?> option) {
        private void writeAppliedValue() {
            try { field.set(config, option.value()); }
            catch (IllegalAccessException exception) { throw new IllegalStateException("Cannot write " + field, exception); }
        }
    }
}
