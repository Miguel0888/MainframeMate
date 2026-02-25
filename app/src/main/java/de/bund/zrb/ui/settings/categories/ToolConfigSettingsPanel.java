package de.bund.zrb.ui.settings.categories;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.bund.zrb.helper.ToolConfigHelper;
import de.bund.zrb.runtime.ToolRegistryImpl;
import de.bund.zrb.ui.settings.SettingsCategory;
import de.zrb.bund.newApi.mcp.McpTool;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

/**
 * Settings panel for per-tool JSON configuration.
 * Shows a list of all registered tools on the left,
 * and a JSON editor on the right for the selected tool's config.
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
        JLabel info = new JLabel("<html><small>JSON-Konfiguration pro Tool. "
                + "Nur Tools mit einer Default-Konfiguration haben hier Einträge.</small></html>");
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

        JButton resetBtn = new JButton("🔄 Default");
        resetBtn.setToolTipText("Setzt die Config des gewählten Tools auf die Default-Werte zurück");
        resetBtn.addActionListener(e -> resetToDefault());

        JPanel bottomPanel = new JPanel(new BorderLayout(4, 0));
        bottomPanel.add(statusLabel, BorderLayout.CENTER);
        bottomPanel.add(resetBtn, BorderLayout.EAST);
        add(bottomPanel, BorderLayout.SOUTH);

        populateToolList();
    }

    private void populateToolList() {
        toolListModel.clear();
        ToolRegistryImpl registry = ToolRegistryImpl.getInstance();
        List<McpTool> tools = registry.getAllTools();

        for (McpTool tool : tools) {
            String name = tool.getSpec().getName();
            // Show all tools that have a non-empty default config or a saved config
            JsonObject defaultConfig = tool.getDefaultConfig();
            JsonObject savedConfig = ToolConfigHelper.getConfig(name);
            if ((defaultConfig != null && defaultConfig.size() > 0) || savedConfig != null) {
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
        JsonObject config = registry.getToolConfig(selected);
        configEditor.setText(new GsonBuilder().setPrettyPrinting().create().toJson(config));
        statusLabel.setText("Tool: " + selected);
    }

    private void saveCurrentConfig() {
        if (currentToolName == null) return;
        String jsonText = configEditor.getText().trim();
        if (jsonText.isEmpty()) return;
        try {
            JsonObject config = JsonParser.parseString(jsonText).getAsJsonObject();
            ToolRegistryImpl.getInstance().setToolConfig(currentToolName, config);
        } catch (Exception e) {
            // Don't overwrite with invalid JSON
            System.err.println("[ToolConfig] Invalid JSON for " + currentToolName + ": " + e.getMessage());
        }
    }

    private void resetToDefault() {
        if (currentToolName == null) return;
        ToolRegistryImpl registry = ToolRegistryImpl.getInstance();
        McpTool tool = registry.getToolByName(currentToolName);
        if (tool != null) {
            JsonObject defaultConfig = tool.getDefaultConfig();
            configEditor.setText(new GsonBuilder().setPrettyPrinting().create().toJson(defaultConfig));
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
        // Save the currently displayed config
        saveCurrentConfig();
        // Persist all to disk
        ToolRegistryImpl.getInstance().saveToolConfigs();
        statusLabel.setText("Tool-Konfigurationen gespeichert.");
    }
}

