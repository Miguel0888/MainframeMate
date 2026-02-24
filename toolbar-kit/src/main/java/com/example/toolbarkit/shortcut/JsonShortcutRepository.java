package com.example.toolbarkit.shortcut;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Persist shortcut mappings as JSON.
 */
public class JsonShortcutRepository implements ShortcutRepository {

    private final Path file;
    private final Gson gson;

    public JsonShortcutRepository(Path file) {
        this(file, new GsonBuilder().setPrettyPrinting().create());
    }

    public JsonShortcutRepository(Path file, Gson gson) {
        if (file == null) throw new IllegalArgumentException("file must not be null");
        this.file = file;
        this.gson = gson != null ? gson : new GsonBuilder().setPrettyPrinting().create();
    }

    @Override
    public Map<String, List<String>> load() {
        if (!Files.exists(file)) {
            return Collections.emptyMap();
        }
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            @SuppressWarnings("unchecked")
            Map<String, List<String>> m = gson.fromJson(reader, Map.class);
            return m != null ? m : Collections.<String, List<String>>emptyMap();
        } catch (IOException ex) {
            System.err.println("Failed to load shortcuts: " + ex.getMessage());
            return Collections.emptyMap();
        }
    }

    @Override
    public void save(Map<String, List<String>> shortcuts) {
        Map<String, List<String>> out = shortcuts != null ? shortcuts : new LinkedHashMap<String, List<String>>();
        try {
            Files.createDirectories(file.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                gson.toJson(out, writer);
            }
        } catch (IOException ex) {
            System.err.println("Failed to save shortcuts: " + ex.getMessage());
        }
    }
}
