package de.bund.zrb.ui.settings;

import de.bund.zrb.runtime.ToolRegistryImpl;
import de.bund.zrb.tools.ToolCategory;
import de.bund.zrb.ui.components.ChatMode;
import de.zrb.bund.newApi.mcp.ToolSpec;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Dialog to configure which tools are available for a specific ChatMode.
 * Tools are grouped by {@link ToolCategory} with section headers and
 * per-group select/deselect buttons.
 * The selected set is persisted in aiConfig as "toolset.&lt;MODE_NAME&gt;"
 * (comma-separated tool names).
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

        // Load currently enabled tools for this mode
        Set<String> enabledTools = loadToolset(currentConfig, mode);

        // Group tools by category, preserving category order
        Map<String, List<ToolSpec>> grouped = new LinkedHashMap<>();
        for (String cat : ToolCategory.ORDERED_CATEGORIES) {
            grouped.put(cat, new ArrayList<ToolSpec>());
        }
        for (ToolSpec spec : allTools) {
            String cat = ToolCategory.getCategory(spec.getName());
            grouped.computeIfAbsent(cat, k -> new ArrayList<>()).add(spec);
        }
        // Sort tools within each category alphabetically
        for (List<ToolSpec> list : grouped.values()) {
            list.sort(Comparator.comparing(ToolSpec::getName, String.CASE_INSENSITIVE_ORDER));
        }

        // Build checkbox panel with category headers
        JPanel checkboxPanel = new JPanel();
        checkboxPanel.setLayout(new BoxLayout(checkboxPanel, BoxLayout.Y_AXIS));
        checkboxPanel.setBorder(new EmptyBorder(4, 4, 4, 4));

        // Parallel lists: allToolsOrdered tracks the ToolSpec order matching checkboxes
        List<ToolSpec> allToolsOrdered = new ArrayList<>();
        List<JCheckBox> checkboxes = new ArrayList<>();

        for (Map.Entry<String, List<ToolSpec>> entry : grouped.entrySet()) {
            String category = entry.getKey();
            List<ToolSpec> tools = entry.getValue();
            if (tools.isEmpty()) continue;

            List<JCheckBox> catCbs = new ArrayList<>();

            // ── Category header ──
            JPanel headerPanel = new JPanel(new BorderLayout(4, 0));
            headerPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
            headerPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY));

            JLabel catLabel = new JLabel("  " + category + " (" + tools.size() + ")");
            catLabel.setFont(catLabel.getFont().deriveFont(Font.BOLD, 12f));
            catLabel.setForeground(new Color(60, 60, 60));
            headerPanel.add(catLabel, BorderLayout.WEST);

            // Group toggle buttons
            JPanel groupBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
            groupBtns.setOpaque(false);
            JButton groupAll = new JButton("\u2713");
            groupAll.setToolTipText("Alle in \"" + category + "\" ausw\u00e4hlen");
            groupAll.setMargin(new Insets(0, 4, 0, 4));
            groupAll.setFont(groupAll.getFont().deriveFont(10f));
            groupAll.addActionListener(e -> { for (JCheckBox cb : catCbs) cb.setSelected(true); });
            JButton groupNone = new JButton("\u2717");
            groupNone.setToolTipText("Keine in \"" + category + "\" ausw\u00e4hlen");
            groupNone.setMargin(new Insets(0, 4, 0, 4));
            groupNone.setFont(groupNone.getFont().deriveFont(10f));
            groupNone.addActionListener(e -> { for (JCheckBox cb : catCbs) cb.setSelected(false); });
            groupBtns.add(groupAll);
            groupBtns.add(groupNone);
            headerPanel.add(groupBtns, BorderLayout.EAST);

            checkboxPanel.add(Box.createVerticalStrut(6));
            checkboxPanel.add(headerPanel);
            checkboxPanel.add(Box.createVerticalStrut(2));

            // ── Tools in this category ──
            for (ToolSpec spec : tools) {
                String name = spec.getName();
                String desc = spec.getDescription();
                String shortDesc = desc != null && desc.length() > 100
                        ? desc.substring(0, 100) + "\u2026" : desc;

                JCheckBox cb = new JCheckBox("<html><b>" + escHtml(name) + "</b>"
                        + (shortDesc != null && !shortDesc.isEmpty()
                            ? " \u2013 <font color='#666666'>" + escHtml(shortDesc) + "</font>"
                            : "")
                        + "</html>");
                cb.setSelected(enabledTools.isEmpty() || enabledTools.contains(name));
                cb.setToolTipText(desc != null
                        ? "<html><body style='width:350px'>" + escHtml(desc) + "</body></html>"
                        : name);
                cb.setBorder(new EmptyBorder(1, 16, 1, 0));

                checkboxes.add(cb);
                catCbs.add(cb);
                allToolsOrdered.add(spec);
                checkboxPanel.add(cb);
            }
        }

        // ── Global buttons ──
        JButton selectAll = new JButton("Alle ausw\u00e4hlen");
        selectAll.addActionListener(e -> { for (JCheckBox cb : checkboxes) cb.setSelected(true); });
        JButton deselectAll = new JButton("Keine ausw\u00e4hlen");
        deselectAll.addActionListener(e -> { for (JCheckBox cb : checkboxes) cb.setSelected(false); });
        JButton invertBtn = new JButton("Auswahl umkehren");
        invertBtn.addActionListener(e -> { for (JCheckBox cb : checkboxes) cb.setSelected(!cb.isSelected()); });

        JPanel buttonBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        buttonBar.add(selectAll);
        buttonBar.add(deselectAll);
        buttonBar.add(invertBtn);

        // ── Info label ──
        JLabel infoLabel = new JLabel("<html><b>Tools f\u00fcr Mode \"" + escHtml(mode.getLabel())
                + "\"</b><br>Aktivierte Tools stehen dem Bot in diesem Mode zur Verf\u00fcgung.</html>");
        infoLabel.setBorder(new EmptyBorder(0, 0, 6, 0));

        // ── Assemble ──
        JPanel container = new JPanel(new BorderLayout(0, 4));
        container.add(infoLabel, BorderLayout.NORTH);
        JScrollPane scrollPane = new JScrollPane(checkboxPanel);
        scrollPane.setPreferredSize(new Dimension(650, 500));
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        container.add(scrollPane, BorderLayout.CENTER);
        container.add(buttonBar, BorderLayout.SOUTH);

        int result = JOptionPane.showConfirmDialog(parent, container,
                "Verf\u00fcgbare Tools \u2013 " + mode.getLabel(),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            List<String> selected = new ArrayList<>();
            for (int i = 0; i < allToolsOrdered.size(); i++) {
                if (checkboxes.get(i).isSelected()) {
                    selected.add(allToolsOrdered.get(i).getName());
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
        Set<String> set = new LinkedHashSet<>();
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
