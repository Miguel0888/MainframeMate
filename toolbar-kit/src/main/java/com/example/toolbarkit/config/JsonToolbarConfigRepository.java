package com.example.toolbarkit.config;

import com.example.toolbarkit.toolbar.ToolbarConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persist toolbar config in JSON on disk.
 */
public class JsonToolbarConfigRepository implements ToolbarConfigRepository {

    private final Path file;
    private final Gson gson;

    public JsonToolbarConfigRepository(Path file) {
        this(file, new GsonBuilder().setPrettyPrinting().create());
    }

    public JsonToolbarConfigRepository(Path file, Gson gson) {
        if (file == null) {
            throw new IllegalArgumentException("file must not be null");
        }
        this.file = file;
        this.gson = (gson != null) ? gson : new GsonBuilder().setPrettyPrinting().create();
    }

    @Override
    public ToolbarConfig loadOrCreate(ConfigFactory defaultFactory) {
        if (!Files.exists(file)) {
            ToolbarConfig cfg = defaultFactory.createDefault();
            save(cfg);
            return cfg;
        }

        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            ToolbarConfig cfg = gson.fromJson(reader, ToolbarConfig.class);
            if (cfg == null) {
                cfg = defaultFactory.createDefault();
                save(cfg);
            }
            return cfg;
        } catch (IOException ex) {
            // Keep the UI running, fall back to defaults.
            ToolbarConfig cfg = defaultFactory.createDefault();
            save(cfg);
            return cfg;
        }
    }

    @Override
    public void save(ToolbarConfig config) {
        try {
            Files.createDirectories(file.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                gson.toJson(config, writer);
            }
        } catch (IOException ex) {
            // Write diagnostics to stderr, never to stdout.
            System.err.println("Failed to save toolbar config: " + ex.getMessage());
        }
    }
}
