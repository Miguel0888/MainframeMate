package de.bund.zrb.tools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.runtime.ToolRegistryImpl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Store tool policies in settings folder. */
public class ToolPolicyRepository {

    private static final File FILE = new File(SettingsHelper.getSettingsFolder(), "tool-policies.json");
    private static final Type LIST_TYPE = new TypeToken<List<ToolPolicy>>() { }.getType();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public List<ToolPolicy> loadAll() {
        List<ToolPolicy> existing = readFile();
        Map<String, ToolPolicy> merged = new LinkedHashMap<>();
        for (ToolPolicy p : existing) {
            if (p != null && p.getToolName() != null) {
                if (p.getAccessType() == null) {
                    p.setAccessType(ToolAccessTypeDefaults.resolveDefault(p.getToolName()));
                }
                merged.put(p.getToolName(), p);
            }
        }

        ToolRegistryImpl.getInstance().getAllTools().forEach(tool -> {
            String name = tool.getSpec().getName();
            if (!merged.containsKey(name)) {
                ToolAccessType access = ToolAccessTypeDefaults.resolveDefault(name);
                boolean ask = access.isWrite();
                merged.put(name, new ToolPolicy(name, true, ask, access));
            }
        });

        List<ToolPolicy> list = new ArrayList<>(merged.values());
        saveAll(list);
        return list;
    }

    public ToolPolicy findByToolName(String toolName) {
        if (toolName == null) {
            return null;
        }
        return loadAll().stream()
                .filter(p -> toolName.equalsIgnoreCase(p.getToolName()))
                .findFirst()
                .orElse(null);
    }

    public void saveAll(List<ToolPolicy> policies) {
        try {
            FILE.getParentFile().mkdirs();
            try (Writer w = new OutputStreamWriter(new FileOutputStream(FILE), StandardCharsets.UTF_8)) {
                GSON.toJson(policies, LIST_TYPE, w);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private List<ToolPolicy> readFile() {
        if (!FILE.exists()) {
            return new ArrayList<>();
        }
        try (Reader r = new InputStreamReader(new FileInputStream(FILE), StandardCharsets.UTF_8)) {
            List<ToolPolicy> policies = GSON.fromJson(r, LIST_TYPE);
            return policies == null ? new ArrayList<>() : policies;
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
}
