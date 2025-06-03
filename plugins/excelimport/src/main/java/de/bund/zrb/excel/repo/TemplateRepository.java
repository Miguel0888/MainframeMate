package de.bund.zrb.excel.repo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import de.bund.zrb.excel.model.ExcelMapping;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TemplateRepository {

    private final File mappingsFile;
    private final Map<String, ExcelMapping> mappings = new LinkedHashMap<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public TemplateRepository(File settingsFolder) {
        this.mappingsFile = new File(settingsFolder, "import.json");
        load();
    }

    public Set<String> getTemplateNames() {
        return mappings.keySet();
    }

    public ExcelMapping getTemplate(String name) {
        return mappings.get(name);
    }

    public void saveTemplate(String name, ExcelMapping mapping) {
        mappings.put(name, mapping);
        save();
    }

    public void deleteTemplate(String name) {
        mappings.remove(name);
        save();
    }

    private void load() {
        if (!mappingsFile.exists()) return;
        try (FileReader reader = new FileReader(mappingsFile)) {
            Type type = new TypeToken<Map<String, ExcelMapping>>() {}.getType();
            Map<String, ExcelMapping> loaded = gson.fromJson(reader, type);
            if (loaded != null) mappings.putAll(loaded);
        } catch (IOException e) {
            e.printStackTrace(); // besser: Logging
        }
    }

    private void save() {
        try (FileWriter writer = new FileWriter(mappingsFile)) {
            gson.toJson(mappings, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
