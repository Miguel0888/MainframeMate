package de.bund.zrb.ui.settings;

import de.bund.zrb.runtime.ToolRegistryImpl;
import de.bund.zrb.ui.components.ChatMode;
import de.zrb.bund.newApi.mcp.ToolSpec;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Dialog to configure which tools are available for a specific ChatMode.
 * Shows all registered tools as checkboxes. The selected set is persisted
 * in aiConfig as "toolset.&lt;MODE_NAME&gt;" (comma-separated tool names).
 */
public class ModeToolsetDialog {

    /**
     * Opens the toolset selection dialog for the given mode.
     *
     * @param parent        parent component for dialog positioning
     * @param mode          the ChatMode to configure
     * @param currentConfig current aiConfig map (read/write)
     * @return true if the user confirmed changes, false if cancelled
     */
    public static boolean show(Component parent, ChatMode mode, Map<String, String> currentConfig) {
        List<ToolSpec> allTools = ToolRegistryImpl.getInstance().getRegisteredToolSpecs();
        allTools.sort(Comparator.comparing(ToolSpec::getName, String.CASE_INSENSITIVE_ORDER));

        // Load currently enabled tools for this mode
        Set<String> enabledTools = loadToolset(currentConfig, mode);

        // Build checkbox panel
        JPanel checkboxPanel = new JPanel();
        checkboxPanel.setLayout(new BoxLayout(checkboxPanel, BoxLayout.Y_AXIS));
        checkboxPanel.setBorder(new EmptyBorder(4, 4, 4, 4));

        List<JCheckBox> checkboxes = new ArrayList<JCheckBox>();
        for (ToolSpec spec : allTools) {
            String name = spec.getName();
            String desc = spec.getDescription();
            String shortDesc = desc != null && desc.length() > 90 ? desc.substring(0, 90) + "…" : desc;

            // Show name + short description in the checkbox label
            String label = name + (shortDesc != null && !shortDesc.isEmpty() ? "  –  " + shortDesc : "");
            JCheckBox cb = new JCheckBox("<html><b>" + name + "</b>"
                    + (shortDesc != null && !shortDesc.isEmpty()
                        ? "<br><font color='gray' size='-2'>" + escHtml(shortDesc) + "</font>"
                        : "")
                    + "</html>");
            cb.setSelected(enabledTools.isEmpty() || enabledTools.contains(name));
            cb.setToolTipText(desc != null ? desc : name);
            checkboxes.add(cb);
            checkboxPanel.add(cb);
            checkboxPanel.add(Box.createVerticalStrut(2));
        }

        // Select all / deselect all buttons
        JButton selectAll = new JButton("Alle auswählen");
        selectAll.addActionListener(e -> {
            for (JCheckBox cb : checkboxes) cb.setSelected(true);
        });
        JButton deselectAll = new JButton("Keine auswählen");
        deselectAll.addActionListener(e -> {
            for (JCheckBox cb : checkboxes) cb.setSelected(false);
        });
        JButton invertBtn = new JButton("Auswahl umkehren");
        invertBtn.addActionListener(e -> {
            for (JCheckBox cb : checkboxes) cb.setSelected(!cb.isSelected());
        });

        JPanel buttonBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        buttonBar.add(selectAll);
        buttonBar.add(deselectAll);
        buttonBar.add(invertBtn);

        // Info label
        JLabel infoLabel = new JLabel("<html><b>Tools für Mode \"" + mode.getLabel()
                + "\"</b><br>Aktivierte Tools stehen dem Bot in diesem Mode zur Verfügung.</html>");
        infoLabel.setBorder(new EmptyBorder(0, 0, 6, 0));

        // Assemble
        JPanel container = new JPanel(new BorderLayout(0, 4));
        container.add(infoLabel, BorderLayout.NORTH);
        JScrollPane scrollPane = new JScrollPane(checkboxPanel);
        scrollPane.setPreferredSize(new Dimension(600, 450));
        container.add(scrollPane, BorderLayout.CENTER);
        container.add(buttonBar, BorderLayout.SOUTH);

        int result = JOptionPane.showConfirmDialog(parent, container,
                "Verfügbare Tools – " + mode.getLabel(),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            // Collect selected tool names
            List<String> selected = new ArrayList<String>();
            for (int i = 0; i < allTools.size(); i++) {
                if (checkboxes.get(i).isSelected()) {
                    selected.add(allTools.get(i).getName());
                }
            }
            saveToolset(currentConfig, mode, selected);
            return true;
        }
        return false;
    }

    /**
     * Load the toolset for a mode from config. Returns empty set if not configured
     * (meaning "all tools enabled").
     */
    public static Set<String> loadToolset(Map<String, String> config, ChatMode mode) {
        String key = "toolset." + mode.name();
        String value = config.get(key);
        if (value == null || value.trim().isEmpty()) {
            return Collections.emptySet(); // empty = all enabled
        }
        Set<String> set = new LinkedHashSet<String>();
        for (String name : value.split(",")) {
            String trimmed = name.trim();
            if (!trimmed.isEmpty()) {
                set.add(trimmed);
            }
        }
        return set;
    }

    /**
     * Save the toolset for a mode to config.
     */
    private static void saveToolset(Map<String, String> config, ChatMode mode, List<String> toolNames) {
        String key = "toolset." + mode.name();
        // If all tools are selected, store empty string (= all enabled, no filtering)
        List<ToolSpec> allTools = ToolRegistryImpl.getInstance().getRegisteredToolSpecs();
        if (toolNames.size() >= allTools.size()) {
            config.remove(key);
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < toolNames.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(toolNames.get(i));
            }
            config.put(key, sb.toString());
        }
    }

    /**
     * Returns true if toolset switching is enabled for the given mode in config.
     */
    public static boolean isToolsetSwitchingEnabled(Map<String, String> config, ChatMode mode) {
        return "true".equals(config.get("toolsetSwitch." + mode.name()));
    }

    /**
     * Sets whether toolset switching is enabled for the given mode.
     */
    public static void setToolsetSwitchingEnabled(Map<String, String> config, ChatMode mode, boolean enabled) {
        config.put("toolsetSwitch." + mode.name(), String.valueOf(enabled));
    }

    private static String escHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
