package de.bund.zrb.helper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.zrb.bund.newApi.workflow.WorkflowStep;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class WorkflowStorage {

    private static final File WORKFLOW_FILE = new File(SettingsHelper.getSettingsFolder(), "workflow.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static Map<String, List<WorkflowStep>> loadWorkflows() {
        if (!WORKFLOW_FILE.exists()) return new LinkedHashMap<>();
        try (Reader reader = new InputStreamReader(new FileInputStream(WORKFLOW_FILE), StandardCharsets.UTF_8)) {
            Map<String, WorkflowStep[]> raw = GSON.fromJson(reader, Map.class);
            Map<String, List<WorkflowStep>> result = new LinkedHashMap<>();
            for (Map.Entry<String, WorkflowStep[]> entry : raw.entrySet()) {
                WorkflowStep[] steps = GSON.fromJson(GSON.toJsonTree(entry.getValue()), WorkflowStep[].class);
                result.put(entry.getKey(), Arrays.asList(steps));
            }
            return result;
        } catch (IOException e) {
            e.printStackTrace();
            return new LinkedHashMap<>();
        }
    }

    public static void saveWorkflows(Map<String, List<WorkflowStep>> workflows) {
        try {
            WORKFLOW_FILE.getParentFile().mkdirs();
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(WORKFLOW_FILE), StandardCharsets.UTF_8)) {
                GSON.toJson(workflows, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void saveWorkflow(String name, List<WorkflowStep> steps) {
        Map<String, List<WorkflowStep>> existing = loadWorkflows();
        existing.put(name, steps);
        saveWorkflows(existing);
    }

    public static List<WorkflowStep> loadWorkflow(String name) {
        Map<String, List<WorkflowStep>> workflows = loadWorkflows();
        return workflows.getOrDefault(name, Collections.emptyList());
    }

    public static Set<String> listWorkflowNames() {
        return loadWorkflows().keySet();
    }

    public static void renameWorkflow(String oldName, String newName) {
        if (oldName.equals(newName)) return;
        Map<String, List<WorkflowStep>> workflows = loadWorkflows();
        if (workflows.containsKey(oldName)) {
            List<WorkflowStep> steps = workflows.remove(oldName);
            workflows.put(newName, steps);
            saveWorkflows(workflows);
        }
    }

    public static void deleteWorkflow(String name) {
        Map<String, List<WorkflowStep>> workflows = loadWorkflows();
        if (workflows.remove(name) != null) {
            saveWorkflows(workflows);
        }
    }

    public static boolean workflowExists(String name) {
        return loadWorkflows().containsKey(name);
    }
}
