package de.bund.zrb.ui.settings.categories;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.bund.zrb.helper.ToolConfigHelper;
import de.bund.zrb.runtime.ToolRegistryImpl;
import de.bund.zrb.ui.settings.SettingsCategory;
import de.zrb.bund.newApi.mcp.McpTool;
import de.zrb.bund.newApi.mcp.ToolConfig;
import de.zrb.bund.newApi.mcp.ToolSpec;
import de.zrb.bund.newApi.mcp.ui.ParameterEditorDialog;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Settings panel for per-tool configuration.
 * Shows a list of all registered tools on the left.
 * On the right, the tool's ToolConfig is shown as editable JSON,
 * with an "Edit..." button that opens the reusable ParameterEditorDialog
 * for structured editing (same dialog as in Workflow StepPanel).
 */
public class ToolConfigSettingsPanel extends JPanel implements SettingsCategory {

    private final JList<String> toolList;
    private final DefaultListModel<String> toolListModel;
    private final JTextArea configEditor;
    private final JLabel statusLabel;
    private String currentToolName;

    public ToolConfigSettingsPanel() {
        setLayout(new BorderLayout(8, 8));
        setBorder(new EmptyBorder(8, 12, 8, 12));

        // Header
        JLabel header = new JLabel("Tool Config");
        header.setFont(header.getFont().deriveFont(Font.BOLD, header.getFont().getSize2D() + 2f));
        JLabel info = new JLabel("<html><small>Konfiguration pro Tool. "
                + "Tools mit Default-Konfiguration oder gespeicherter Konfiguration erscheinen hier.</small></html>");
        info.setForeground(Color.GRAY);
        JPanel headerPanel = new JPanel(new BorderLayout(4, 4));
        headerPanel.add(header, BorderLayout.NORTH);
        headerPanel.add(info, BorderLayout.SOUTH);
        add(headerPanel, BorderLayout.NORTH);

        // Left: Tool list
        toolListModel = new DefaultListModel<>();
        toolList = new JList<>(toolListModel);
        toolList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        toolList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                saveCurrentConfig();
                loadSelectedToolConfig();
            }
        });
        JScrollPane listScroll = new JScrollPane(toolList);
        listScroll.setPreferredSize(new Dimension(220, 300));
        add(listScroll, BorderLayout.WEST);

        // Right: JSON editor
        configEditor = new JTextArea();
        configEditor.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        configEditor.setTabSize(2);
        JScrollPane editorScroll = new JScrollPane(configEditor);
        add(editorScroll, BorderLayout.CENTER);

        // Bottom: status + buttons
        statusLabel = new JLabel(" ");
        statusLabel.setForeground(Color.GRAY);

        JButton editBtn = new JButton("✏ Bearbeiten…");
        editBtn.setToolTipText("Öffnet den Parameter-Editor mit strukturierter Eingabe");
        editBtn.addActionListener(e -> openParameterEditor());

        JButton resetBtn = new JButton("🔄 Default");
        resetBtn.setToolTipText("Setzt die Config auf die Default-Werte zurück");
        resetBtn.addActionListener(e -> resetToDefault());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        buttonPanel.add(editBtn);
        buttonPanel.add(resetBtn);

        JPanel bottomPanel = new JPanel(new BorderLayout(4, 0));
        bottomPanel.add(statusLabel, BorderLayout.CENTER);
        bottomPanel.add(buttonPanel, BorderLayout.EAST);
        add(bottomPanel, BorderLayout.SOUTH);

        populateToolList();
    }

    private void populateToolList() {
        toolListModel.clear();
        ToolRegistryImpl registry = ToolRegistryImpl.getInstance();
        List<McpTool> tools = registry.getAllTools();

        for (McpTool tool : tools) {
            String name = tool.getSpec().getName();
            ToolConfig defaultConfig = tool.getDefaultConfig();
            ToolConfig savedConfig = ToolConfigHelper.getConfig(name);
            if ((defaultConfig != null && !defaultConfig.isEmpty()) || savedConfig != null) {
                toolListModel.addElement(name);
            }
        }

        if (!toolListModel.isEmpty()) {
            toolList.setSelectedIndex(0);
        }
    }

    private void loadSelectedToolConfig() {
        String selected = toolList.getSelectedValue();
        if (selected == null) {
            configEditor.setText("");
            currentToolName = null;
            return;
        }
        currentToolName = selected;
        ToolRegistryImpl registry = ToolRegistryImpl.getInstance();
        ToolConfig config = registry.getToolConfig(selected);
        configEditor.setText(config.toPrettyJson());
        statusLabel.setText("Tool: " + selected);
    }

    private void saveCurrentConfig() {
        if (currentToolName == null) return;
        String jsonText = configEditor.getText().trim();
        if (jsonText.isEmpty()) return;
        try {
            JsonObject jsonObj = JsonParser.parseString(jsonText).getAsJsonObject();
            ToolRegistryImpl registry = ToolRegistryImpl.getInstance();
            McpTool tool = registry.getToolByName(currentToolName);
            ToolConfig config;
            if (tool != null) {
                config = ToolConfig.fromJson(jsonObj, tool.getConfigClass());
            } else {
                config = ToolConfig.fromJson(jsonObj, ToolConfig.class);
            }
            registry.setToolConfig(currentToolName, config);
        } catch (Exception e) {
            System.err.println("[ToolConfig] Invalid JSON for " + currentToolName + ": " + e.getMessage());
        }
    }

    /**
     * Opens the reusable ParameterEditorDialog for structured editing of the tool's config.
     * The config fields are exposed as an InputSchema so the same dialog works for both
     * workflow parameters and tool settings.
     */
    private void openParameterEditor() {
        if (currentToolName == null) return;

        ToolRegistryImpl registry = ToolRegistryImpl.getInstance();
        McpTool tool = registry.getToolByName(currentToolName);
        if (tool == null) return;

        // Build an InputSchema from the current config JSON fields
        ToolConfig currentConfig = registry.getToolConfig(currentToolName);
        JsonObject configJson = currentConfig.toJson();

        // Create a synthetic schema from config fields
        Map<String, ToolSpec.Property> properties = new LinkedHashMap<>();
        Map<String, Object> currentValues = new LinkedHashMap<>();

        for (Map.Entry<String, com.google.gson.JsonElement> entry : configJson.entrySet()) {
            String key = entry.getKey();
            com.google.gson.JsonElement val = entry.getValue();

            String type = "string";
            Object javaValue = null;
            if (val.isJsonPrimitive()) {
                if (val.getAsJsonPrimitive().isBoolean()) {
                    type = "boolean";
                    javaValue = val.getAsBoolean();
                } else if (val.getAsJsonPrimitive().isNumber()) {
                    type = "number";
                    javaValue = val.getAsNumber();
                } else {
                    javaValue = val.getAsString();
                }
            } else if (!val.isJsonNull()) {
                javaValue = val.toString();
            }

            properties.put(key, new ToolSpec.Property(type, key));
            if (javaValue != null) {
                currentValues.put(key, javaValue);
            }
        }

        if (properties.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Diese Tool-Config hat keine bearbeitbaren Felder.",
                    "Tool Config", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        ToolSpec.InputSchema schema = new ToolSpec.InputSchema(properties, java.util.Collections.emptyList());

        Map<String, Object> edited = ParameterEditorDialog.showDialogForSchema(
                this, schema, currentValues, "Config bearbeiten: " + currentToolName);

        if (edited != null) {
            // Merge edited values back into the config JSON
            JsonObject newJson = new JsonObject();
            for (Map.Entry<String, Object> entry : edited.entrySet()) {
                Object val = entry.getValue();
                if (val instanceof Boolean) {
                    newJson.addProperty(entry.getKey(), (Boolean) val);
                } else if (val instanceof Number) {
                    newJson.addProperty(entry.getKey(), (Number) val);
                } else {
                    newJson.addProperty(entry.getKey(), String.valueOf(val));
                }
            }
            // Add fields from original that weren't edited (preserving structure)
            for (Map.Entry<String, com.google.gson.JsonElement> entry : configJson.entrySet()) {
                if (!newJson.has(entry.getKey())) {
                    newJson.add(entry.getKey(), entry.getValue());
                }
            }

            ToolConfig updatedConfig = ToolConfig.fromJson(newJson, tool.getConfigClass());
            registry.setToolConfig(currentToolName, updatedConfig);
            configEditor.setText(updatedConfig.toPrettyJson());
            statusLabel.setText("Config aktualisiert: " + currentToolName);
        }
    }

    private void resetToDefault() {
        if (currentToolName == null) return;
        ToolRegistryImpl registry = ToolRegistryImpl.getInstance();
        McpTool tool = registry.getToolByName(currentToolName);
        if (tool != null) {
            ToolConfig defaultConfig = tool.getDefaultConfig();
            configEditor.setText(defaultConfig.toPrettyJson());
            registry.setToolConfig(currentToolName, defaultConfig);
            statusLabel.setText("Default-Config geladen für: " + currentToolName);
        }
    }

    @Override
    public String getId() {
        return "toolConfig";
    }

    @Override
    public String getTitle() {
        return "Tool Config";
    }

    @Override
    public JComponent getComponent() {
        return this;
    }

    @Override
    public void apply() {
        saveCurrentConfig();
        ToolRegistryImpl.getInstance().saveToolConfigs();
        statusLabel.setText("Tool-Konfigurationen gespeichert.");
    }
}

