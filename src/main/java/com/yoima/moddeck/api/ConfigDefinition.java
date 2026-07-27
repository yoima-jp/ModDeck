package com.yoima.moddeck.api;

import com.yoima.moddeck.api.option.*;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Immutable screen structure containing mutable typed option values. */
public final class ConfigDefinition {
    private static final Logger LOGGER = Logger.getLogger(ConfigDefinition.class.getName());
    private final String modId;
    private final ConfigRoute route;
    private final ConfigText title;
    private final ConfigText description;
    private final List<ConfigCategory> categories;
    private final ConfigScreenStyle style;
    private final Runnable saveCallback;

    private ConfigDefinition(String modId, ConfigRoute route, ConfigText title, ConfigText description,
                             List<ConfigCategory> categories, ConfigScreenStyle style, Runnable saveCallback) {
        this.modId = modId;
        this.route = route;
        this.title = title;
        this.description = description;
        this.categories = List.copyOf(categories);
        this.style = style;
        this.saveCallback = saveCallback;
    }

    public static Builder builder(String modId) { return new Builder(modId); }
    public String modId() { return modId; }
    public ConfigRoute route() { return route; }
    /** @deprecated Prefer {@link #titleText()}. */
    @Deprecated public String title() { return title.component().getString(); }
    /** @deprecated Prefer {@link #descriptionText()}. */
    @Deprecated public String description() { return description.component().getString(); }
    public ConfigText titleText() { return title; }
    public ConfigText descriptionText() { return description; }
    public List<ConfigCategory> categories() { return categories; }
    public ConfigScreenStyle style() { return style; }
    public boolean isValid() {
        return categories.stream().flatMap(category -> flatten(category.options()).stream())
                .noneMatch(option -> option.validationError().isPresent());
    }
    public boolean isDirty() {
        return categories.stream().flatMap(category -> category.options().stream())
                .anyMatch(ConfigOption::isDirty);
    }

    public Optional<ConfigOption<?>> option(String categoryId, String optionId) {
        return categories.stream().filter(category -> category.id().equals(categoryId))
                .flatMap(category -> flatten(category.options()).stream())
                .filter(option -> option.id().equals(optionId)).findFirst();
    }

    public void reset() { categories.forEach(category -> category.options().forEach(ConfigOption::reset)); }
    public void discardChanges() {
        categories.forEach(category -> category.options().forEach(ConfigOption::discardChanges));
    }

    private static List<ConfigOption<?>> flatten(List<ConfigOption<?>> options) {
        List<ConfigOption<?>> flattened = new ArrayList<>();
        for (ConfigOption<?> option : options) {
            flattened.add(option);
            if (option instanceof SubcategoryOption subcategory) flattened.addAll(flatten(subcategory.children()));
        }
        return List.copyOf(flattened);
    }

    /** Called by ConfigScreenApi only after persistence completes successfully. */
    void notifySaved() {
        categories.forEach(category -> category.options().forEach(ConfigOption::notifySaved));
        try {
            saveCallback.run();
        } catch (RuntimeException exception) {
            LOGGER.log(Level.SEVERE, "Config save callback failed for " + modId, exception);
        }
    }

    public static final class Builder {
        private final String modId;
        private ConfigRoute route;
        private ConfigText title;
        private ConfigText description = ConfigText.empty();
        private final LinkedHashMap<String, CategoryBuilder> categories = new LinkedHashMap<>();
        private CategoryBuilder currentCategory;
        private ConfigScreenStyle style = ConfigScreenStyle.DEFAULT;
        private Runnable saveCallback = () -> {};
        private boolean editable = true;
        private int nextCategoryOrder;

        private Builder(String modId) {
            if (modId == null || !modId.matches("[a-z0-9_.-]+")) {
                throw new IllegalArgumentException("modId must match [a-z0-9_.-]+");
            }
            this.modId = modId;
            this.route = ConfigRoute.forMod(modId);
            this.title = ConfigText.literal(modId);
        }

        public Builder route(ConfigRoute route) { this.route = Objects.requireNonNull(route, "route"); return this; }
        public Builder title(String title) { return title(ConfigText.literal(title)); }
        public Builder titleKey(String key, Object... arguments) { return title(ConfigText.translatable(key, arguments)); }
        public Builder title(ConfigText title) { this.title = Objects.requireNonNull(title, "title"); return this; }
        public Builder description(String description) { return description(ConfigText.literal(description)); }
        public Builder descriptionKey(String key, Object... arguments) {
            return description(ConfigText.translatable(key, arguments));
        }
        public Builder description(ConfigText description) {
            this.description = Objects.requireNonNull(description, "description");
            return this;
        }
        public Builder style(ConfigScreenStyle style) { this.style = Objects.requireNonNull(style, "style"); return this; }
        public Builder onSave(Runnable callback) {
            this.saveCallback = Objects.requireNonNull(callback, "callback");
            return this;
        }
        public Builder editable(boolean editable) { this.editable = editable; return this; }

        public Builder category(String id, String displayName) {
            return category(id, ConfigText.literal(displayName), nextCategoryOrder++);
        }
        public Builder categoryKey(String id, String translationKey) {
            return category(id, ConfigText.translatable(translationKey), nextCategoryOrder++);
        }
        public Builder category(String id, ConfigText displayName) {
            return category(id, displayName, nextCategoryOrder++);
        }
        public Builder category(String id, ConfigText displayName, int order) {
            if (categories.containsKey(id)) throw new IllegalArgumentException("Duplicate category id: " + id);
            currentCategory = new CategoryBuilder(id, displayName, order);
            categories.put(id, currentCategory);
            nextCategoryOrder = Math.max(nextCategoryOrder, order + 1);
            return this;
        }

        public Builder addOption(ConfigOption<?> option) { return add(option); }
        public Builder descriptionEntry(String id, ConfigText text) {
            return add(new DescriptionOption(id, text));
        }

        public Builder buttonOption(String id, String name, Runnable action) {
            return buttonOption(id, ConfigText.literal(name), ConfigText.empty(),
                    ConfigText.literal(name), action);
        }
        public Builder buttonOption(String id, ConfigText name, ConfigText description,
                                    ConfigText buttonText, Runnable action) {
            return add(new ButtonOption(id, name, description, buttonText, action));
        }

        public Builder booleanOption(String id, String name, boolean defaultValue) {
            return booleanOption(id, name, "", defaultValue);
        }
        public Builder booleanOption(String id, String name, String description, boolean defaultValue) {
            return add(new BooleanOption(id, name, description, defaultValue));
        }
        public Builder booleanOption(String id, ConfigText name, ConfigText description, boolean defaultValue) {
            return add(new BooleanOption(id, name, description, defaultValue));
        }
        public Builder booleanOptionKey(String id, String nameKey, String descriptionKey, boolean defaultValue) {
            return booleanOption(id, ConfigText.translatable(nameKey), optionalKey(descriptionKey), defaultValue);
        }

        public Builder integerOption(String id, String name, int defaultValue, int min, int max) {
            return integerOption(id, name, "", defaultValue, min, max, 1);
        }
        public Builder integerOption(String id, String name, String description, int defaultValue,
                                     int min, int max, int step) {
            return add(new IntegerOption(id, name, description, defaultValue, min, max, step));
        }
        public Builder integerOption(String id, ConfigText name, ConfigText description, int defaultValue,
                                     int min, int max, int step) {
            return add(new IntegerOption(id, name, description, defaultValue, min, max, step));
        }

        public Builder longOption(String id, ConfigText name, ConfigText description, long defaultValue,
                                  long min, long max, long step) {
            return add(new LongOption(id, name, description, defaultValue, min, max, step));
        }
        public Builder floatOption(String id, ConfigText name, ConfigText description, float defaultValue,
                                   float min, float max, float step) {
            return add(new FloatOption(id, name, description, defaultValue, min, max, step));
        }
        public Builder doubleOption(String id, String name, double defaultValue, double min, double max) {
            return doubleOption(id, name, "", defaultValue, min, max, 0.01);
        }
        public Builder doubleOption(String id, String name, String description, double defaultValue,
                                    double min, double max, double step) {
            return add(new DoubleOption(id, name, description, defaultValue, min, max, step));
        }
        public Builder doubleOption(String id, ConfigText name, ConfigText description, double defaultValue,
                                    double min, double max, double step) {
            return add(new DoubleOption(id, name, description, defaultValue, min, max, step));
        }

        public Builder stringOption(String id, String name, String defaultValue) {
            return stringOption(id, name, "", defaultValue, 256);
        }
        public Builder stringOption(String id, String name, String description, String defaultValue,
                                    int maximumLength) {
            return add(new StringOption(id, name, description, defaultValue, maximumLength));
        }
        public Builder stringOption(String id, ConfigText name, ConfigText description, String defaultValue,
                                    int maximumLength) {
            return add(new StringOption(id, name, description, defaultValue, maximumLength));
        }

        public <E extends Enum<E>> Builder enumOption(String id, String name, E defaultValue, Class<E> type) {
            return enumOption(id, name, "", defaultValue, type);
        }
        public <E extends Enum<E>> Builder enumOption(String id, String name, String description,
                                                       E defaultValue, Class<E> type) {
            return add(new EnumOption<>(id, name, description, defaultValue, type));
        }
        public <E extends Enum<E>> Builder enumOption(String id, ConfigText name, ConfigText description,
                                                       E defaultValue, Class<E> type) {
            return add(new EnumOption<>(id, name, description, defaultValue, type));
        }

        public Builder colorOption(String id, ConfigText name, ConfigText description,
                                   int defaultValue, boolean alpha) {
            return add(new ColorOption(id, name, description, defaultValue, alpha));
        }
        public Builder keybindOption(String id, ConfigText name, ConfigText description, String defaultKey) {
            return add(new KeybindOption(id, name, description, defaultKey));
        }
        public Builder keybindOption(String id, ConfigText name, ConfigText description, String defaultKey,
                                     Set<KeybindOption.InputType> allowedInputs, boolean allowUnbound) {
            return add(new KeybindOption(id, name, description, defaultKey, allowedInputs, allowUnbound));
        }
        public <T> Builder selectorOption(String id, ConfigText name, ConfigText description, T defaultValue,
                                          List<T> choices, ValueCodec<T> codec,
                                          Function<T, ConfigText> labelFactory) {
            return add(new SelectorOption<>(id, name, description, defaultValue, choices, codec, labelFactory));
        }
        public <T> Builder listOption(String id, ConfigText name, ConfigText description, List<T> defaultValue,
                                      ValueCodec<T> codec, int minimumSize, int maximumSize) {
            return add(new ListOption<>(id, name, description, defaultValue, codec, minimumSize, maximumSize));
        }

        public ConfigDefinition build() {
            if (categories.isEmpty()) throw new IllegalStateException("At least one category is required");
            List<ConfigCategory> built = categories.values().stream()
                    .sorted(Comparator.comparingInt(category -> category.order))
                    .map(CategoryBuilder::build).toList();
            if (!editable) built.forEach(category -> setEditable(category.options(), false));
            return new ConfigDefinition(modId, route, title, description, built, style, saveCallback);
        }

        private static void setEditable(List<ConfigOption<?>> options, boolean editable) {
            for (ConfigOption<?> option : options) {
                option.editable(editable);
                if (option instanceof SubcategoryOption subcategory) setEditable(subcategory.children(), editable);
            }
        }

        private Builder add(ConfigOption<?> option) {
            if (currentCategory == null) throw new IllegalStateException("Call category() before adding options");
            currentCategory.add(Objects.requireNonNull(option, "option"));
            return this;
        }

        private static ConfigText optionalKey(String key) {
            return key == null || key.isBlank() ? ConfigText.empty() : ConfigText.translatable(key);
        }
    }

    private static final class CategoryBuilder {
        private final String id;
        private final ConfigText displayName;
        private final int order;
        private final LinkedHashMap<String, ConfigOption<?>> options = new LinkedHashMap<>();

        private CategoryBuilder(String id, ConfigText displayName, int order) {
            if (id == null || !id.matches("[a-z0-9_.-]+")) throw new IllegalArgumentException("Invalid category id: " + id);
            this.id = id;
            this.displayName = Objects.requireNonNull(displayName, "displayName");
            this.order = order;
        }
        private void add(ConfigOption<?> option) {
            if (options.putIfAbsent(option.id(), option) != null) {
                throw new IllegalArgumentException("Duplicate option id in category " + id + ": " + option.id());
            }
        }
        private ConfigCategory build() { return new ConfigCategory(id, displayName, order, List.copyOf(options.values())); }
    }
}
