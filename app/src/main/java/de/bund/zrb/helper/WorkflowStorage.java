package de.bund.zrb.helper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import de.zrb.bund.newApi.workflow.WorkflowTemplate;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class WorkflowStorage {

    private static final File WORKFLOW_FILE = new File(SettingsHelper.getSettingsFolder(), "workflow.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static Map<String, WorkflowTemplate> loadWorkflows() {
        if (!WORKFLOW_FILE.exists()) return new LinkedHashMap<>();
        try (Reader reader = new InputStreamReader(new FileInputStream(WORKFLOW_FILE), StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, WorkflowTemplate>>() {}.getType();
            return GSON.fromJson(reader, type);
        } catch (IOException e) {
            e.printStackTrace();
            return new LinkedHashMap<>();
        }
    }

    public static void saveWorkflows(Map<String, WorkflowTemplate> workflows) {
        try {
            WORKFLOW_FILE.getParentFile().mkdirs();
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(WORKFLOW_FILE), StandardCharsets.UTF_8)) {
                GSON.toJson(workflows, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void saveWorkflow(String name, WorkflowTemplate template) {
        Map<String, WorkflowTemplate> all = loadWorkflows();
        all.put(name, template);
        saveWorkflows(all);
    }

    public static WorkflowTemplate loadWorkflow(String name) {
        return loadWorkflows().getOrDefault(name, new WorkflowTemplate());
    }

    public static Set<String> listWorkflowNames() {
        return loadWorkflows().keySet();
    }

    public static void renameWorkflow(String oldName, String newName) {
        if (oldName.equals(newName)) return;
        Map<String, WorkflowTemplate> workflows = loadWorkflows();
        if (workflows.containsKey(oldName)) {
            WorkflowTemplate template = workflows.remove(oldName);
            workflows.put(newName, template);
            saveWorkflows(workflows);
        }
    }

    public static void deleteWorkflow(String name) {
        Map<String, WorkflowTemplate> workflows = loadWorkflows();
        if (workflows.remove(name) != null) {
            saveWorkflows(workflows);
        }
    }

    public static boolean workflowExists(String name) {
        return loadWorkflows().containsKey(name);
    }
}
