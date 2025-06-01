package de.bund.zrb.helper;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import de.zrb.bund.newApi.mcp.ToolSpec;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ToolSettingsHelper {

    private static final File TOOL_FILE = new File(SettingsHelper.getSettingsFolder(), "tools.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Type TOOL_LIST_TYPE = new TypeToken<List<ToolSpec>>() {}.getType();

    /**
     * Lädt alle gespeicherten Tool-Definitionen.
     */
    public static List<ToolSpec> loadTools() {
        if (!TOOL_FILE.exists()) return new ArrayList<>();
        try (Reader reader = new InputStreamReader(new FileInputStream(TOOL_FILE), StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, TOOL_LIST_TYPE);
        } catch (IOException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /**
     * Speichert die übergebenen Tools dauerhaft.
     */
    public static void saveTools(List<ToolSpec> tools) {
        try {
            TOOL_FILE.getParentFile().mkdirs();
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(TOOL_FILE), StandardCharsets.UTF_8)) {
                GSON.toJson(tools, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Fügt ein Tool hinzu und speichert es.
     */
    public static void addTool(ToolSpec newTool) {
        List<ToolSpec> tools = loadTools();
        tools.add(newTool);
        saveTools(tools);
    }

    /**
     * Entfernt ein Tool anhand des Namens.
     */
    public static void removeToolByName(String name) {
        List<ToolSpec> tools = loadTools();
        tools.removeIf(t -> t.getName().equalsIgnoreCase(name));
        saveTools(tools);
    }

    /**
     * Sucht ein Tool anhand des Namens.
     */
    public static ToolSpec findToolByName(String name) {
        return loadTools().stream()
                .filter(t -> t.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }
}