package com.yoima.moddeck.storage;

import com.google.gson.*;
import com.yoima.moddeck.api.*;
import com.yoima.moddeck.api.option.ConfigOption;
import com.yoima.moddeck.api.storage.ConfigStorage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;
import java.util.logging.*;

/** UTF-8 JSON persistence with atomic replacement and per-option fault isolation. */
public final class JsonConfigStorage implements ConfigStorage {
    private static final Logger LOGGER = Logger.getLogger(JsonConfigStorage.class.getName());
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path directory;

    public JsonConfigStorage(Path directory) { this.directory = directory.toAbsolutePath().normalize(); }

    @Override public void load(ConfigDefinition definition) throws IOException {
        Path file = fileFor(definition);
        if (!Files.exists(file)) return;
        JsonObject root;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) throw new IOException("Config root must be a JSON object: " + file);
            root = parsed.getAsJsonObject();
        } catch (JsonParseException exception) {
            throw new IOException("Invalid JSON in " + file, exception);
        }
        for (ConfigCategory category : definition.categories()) {
            JsonObject values = root.has(category.id()) && root.get(category.id()).isJsonObject()
                    ? root.getAsJsonObject(category.id()) : null;
            if (values == null) continue;
            loadOptions(category.options(), values, category.id(), file);
        }
    }

    @Override public void save(ConfigDefinition definition) throws IOException {
        Files.createDirectories(directory);
        JsonObject root = new JsonObject();
        for (ConfigCategory category : definition.categories()) {
            JsonObject values = new JsonObject();
            saveOptions(category.options(), values);
            root.add(category.id(), values);
        }
        Path target = fileFor(definition);
        Path temporary = Files.createTempFile(directory, definition.modId() + "-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                GSON.toJson(root, writer);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private Path fileFor(ConfigDefinition definition) {
        Path file = directory.resolve(definition.modId() + ".json").normalize();
        if (!file.getParent().equals(directory)) throw new IllegalArgumentException("Unsafe mod id: " + definition.modId());
        return file;
    }

    private static void loadOptions(java.util.List<ConfigOption<?>> options, JsonObject values,
                                    String path, Path file) {
        for (ConfigOption<?> option : options) {
            if (option instanceof com.yoima.moddeck.api.option.SubcategoryOption subcategory) {
                JsonObject childValues = values.has(option.id()) && values.get(option.id()).isJsonObject()
                        ? values.getAsJsonObject(option.id()) : null;
                if (childValues != null) loadOptions(subcategory.children(), childValues, path + "." + option.id(), file);
                continue;
            }
            JsonElement element = values.get(option.id());
            if (element == null || element.isJsonNull()) continue;
            try {
                setDecoded(option, storageValue(element));
            } catch (RuntimeException exception) {
                LOGGER.log(Level.WARNING, "Ignoring invalid value " + path + "." + option.id() + " in " + file, exception);
            }
        }
    }

    private static void saveOptions(java.util.List<ConfigOption<?>> options, JsonObject values) {
        for (ConfigOption<?> option : options) {
            if (option instanceof com.yoima.moddeck.api.option.SubcategoryOption subcategory) {
                JsonObject childValues = new JsonObject();
                saveOptions(subcategory.children(), childValues);
                values.add(option.id(), childValues);
            } else if (option.persistent()) {
                values.add(option.id(), GSON.toJsonTree(option.encodeDraft()));
            }
        }
    }

    private static Object storageValue(JsonElement element) {
        if (element.isJsonArray()) {
            java.util.List<Object> values = new java.util.ArrayList<>();
            for (JsonElement child : element.getAsJsonArray()) values.add(storageValue(child));
            return java.util.List.copyOf(values);
        }
        if (!element.isJsonPrimitive()) throw new IllegalArgumentException("Expected primitive or array JSON value");
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isBoolean()) return primitive.getAsBoolean();
        if (primitive.isNumber()) return primitive.getAsNumber();
        return primitive.getAsString();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void setDecoded(ConfigOption option, Object rawValue) {
        option.loadEncodedValue(rawValue);
    }
}
