package de.bund.zrb.runtime;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import de.bund.zrb.ui.settings.ExpressionExamples;
import de.zrb.bund.api.ExpressionRegistry;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;

/**
 * Gson-basierte Implementierung der ExpressionRegistry, speichert Ausdrucksdefinitionen als komplette Callable-Klassen.
 */
public class ExpressionRegistryImpl implements ExpressionRegistry {

    private static final String FILE_NAME = "expressions.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();

    private static ExpressionRegistryImpl instance;

    private final Map<String, String> expressions = new LinkedHashMap<>();
    private final File file;
    private final ExpressionCompiler expressionCompiler = ExpressionCompilerFactory.getCompiler();

    private ExpressionRegistryImpl() {
        this.file = new File(getSettingsFolder(), FILE_NAME);
        reload();
    }

    public static synchronized ExpressionRegistryImpl getInstance() {
        if (instance == null) {
            instance = new ExpressionRegistryImpl();
        }
        return instance;
    }

    @Override
    public void register(String key, String fullSourceCode) {
        expressions.put(key, fullSourceCode);
    }

    @Override
    public Optional<String> getCode(String key) {
        return Optional.ofNullable(expressions.get(key));
    }

    @Override
    public String getSource(String key) {
        return expressions.getOrDefault(key, "");
    }

    @Override
    public String evaluate(String key, List<String> args) throws Exception {
        // Lazy-Fallback aus Examples
        if (!expressions.containsKey(key)) {
            String example = ExpressionExamples.getExamples().get(key);
            if (example != null) {
                register(key, example);
                save(); // optional
            }
        }

        String source = expressions.get(key);
        if (source == null || source.trim().isEmpty()) {
            throw new IllegalArgumentException("Kein Quelltext für Ausdruck: '" + key + "'");
        }

        return expressionCompiler.compileAndExecute(key, source, args);
    }


    @Override
    public void remove(String key) {
        expressions.remove(key);
    }

    @Override
    public Set<String> getKeys() {
        return expressions.keySet();
    }

    @Override
    public void reload() {
        expressions.clear();
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                Map<String, String> loaded = GSON.fromJson(reader, MAP_TYPE);
                if (loaded != null) {
                    expressions.putAll(loaded);
                }
            } catch (IOException e) {
                System.err.println("⚠ Fehler beim Laden der Expressions: " + e.getMessage());
            }
        }
        ExpressionExamples.ensureExamplesRegistered(this);
    }

    @Override
    public void save() {
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(expressions, writer);
        } catch (IOException e) {
            System.err.println("⚠ Fehler beim Speichern der Expressions: " + e.getMessage());
        }
    }

    @Override
    public Set<String> getFunctionNames() {
        return new HashSet<>(expressions.keySet());
    }

    private File getSettingsFolder() {
        return de.bund.zrb.helper.SettingsHelper.getSettingsFolder();
    }

}
