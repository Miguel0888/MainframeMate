package de.bund.zrb.ui.commands;

import de.bund.zrb.BuildInfo;
import de.bund.zrb.ui.branding.IconThemeInstaller;
import de.zrb.bund.api.ShortcutMenuCommand;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ShowAboutDialogMenuCommand extends ShortcutMenuCommand {

    private static final Logger LOG = Logger.getLogger(ShowAboutDialogMenuCommand.class.getName());

    private final JFrame parent;

    public ShowAboutDialogMenuCommand(JFrame parent) {
        this.parent = parent;
    }

    @Override
    public String getId() {
        return "help.about";
    }

    @Override
    public String getLabel() {
        return "ℹ Über MainframeMate";
    }

    @Override
    public void perform() {
        // Use the largest available app logo instead of the default info icon
        ImageIcon logoIcon = null;
        Image appIcon = IconThemeInstaller.getAppIcon(1024);
        if (appIcon == null) {
            appIcon = IconThemeInstaller.getAppIcon(256);
        }
        if (appIcon != null) {
            // Scale to a nice display size for the dialog (128px)
            Image scaled = appIcon.getScaledInstance(128, 128, Image.SCALE_SMOOTH);
            logoIcon = new ImageIcon(scaled);
        }

        // --- Build the about panel ---
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        // Logo
        if (logoIcon != null) {
            JLabel logoLabel = new JLabel(logoIcon);
            logoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            panel.add(logoLabel);
            panel.add(Box.createVerticalStrut(12));
        }

        // App name + version + copyright
        JLabel nameLabel = new JLabel("MainframeMate");
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 18f));
        nameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(nameLabel);

        JLabel versionLabel = new JLabel("Version " + BuildInfo.getVersion());
        versionLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(versionLabel);

        JLabel copyrightLabel = new JLabel("© 2026 GZD");
        copyrightLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(copyrightLabel);

        panel.add(Box.createVerticalStrut(16));

        // Separator
        JSeparator sep = new JSeparator();
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 2));
        panel.add(sep);
        panel.add(Box.createVerticalStrut(8));

        // --- Tabbed pane for License + OSS ---
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Tab 1: MIT License
        String licenseText =
                "MIT License\n\n" +
                "Copyright (c) 2026 GZD\n\n" +
                "Permission is hereby granted, free of charge, to any person obtaining a copy\n" +
                "of this software and associated documentation files (the \"Software\"), to deal\n" +
                "in the Software without restriction, including without limitation the rights\n" +
                "to use, copy, modify, merge, publish, distribute, sublicense, and/or sell\n" +
                "copies of the Software, and to permit persons to whom the Software is\n" +
                "furnished to do so, subject to the following conditions:\n\n" +
                "The above copyright notice and this permission notice shall be included in all\n" +
                "copies or substantial portions of the Software.\n\n" +
                "THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR\n" +
                "IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,\n" +
                "FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE\n" +
                "AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER\n" +
                "LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,\n" +
                "OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE\n" +
                "SOFTWARE.";

        JTextArea licenseArea = new JTextArea(licenseText);
        licenseArea.setEditable(false);
        licenseArea.setLineWrap(true);
        licenseArea.setWrapStyleWord(true);
        licenseArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        licenseArea.setBackground(panel.getBackground());
        licenseArea.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        JScrollPane licenseScroll = new JScrollPane(licenseArea);
        licenseScroll.setPreferredSize(new Dimension(600, 220));
        tabbedPane.addTab("Lizenz", licenseScroll);

        // Tab 2: Open Source Dependencies
        JPanel ossPanel = buildOssPanel(panel.getBackground());
        tabbedPane.addTab("Open-Source-Bibliotheken", ossPanel);

        panel.add(tabbedPane);

        JOptionPane.showMessageDialog(parent,
                panel,
                "Über MainframeMate",
                JOptionPane.PLAIN_MESSAGE);
    }

    /**
     * Builds the OSS dependencies panel with a table loaded from oss-licenses.json
     * and a disclaimer that third-party libraries may use different licenses.
     */
    private JPanel buildOssPanel(Color bg) {
        JPanel ossPanel = new JPanel(new BorderLayout(0, 8));

        // Disclaimer at top
        JTextArea disclaimer = new JTextArea(
                "MainframeMate verwendet die folgenden Open-Source-Bibliotheken.\n" +
                "Diese Bibliotheken können einer anderen Lizenz als der MIT-Lizenz dieses Projekts unterliegen.\n" +
                "Bitte beachten Sie die jeweilige Lizenz der einzelnen Bibliothek.");
        disclaimer.setEditable(false);
        disclaimer.setLineWrap(true);
        disclaimer.setWrapStyleWord(true);
        disclaimer.setFont(disclaimer.getFont().deriveFont(Font.ITALIC, 11f));
        disclaimer.setBackground(bg);
        disclaimer.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        ossPanel.add(disclaimer, BorderLayout.NORTH);

        // Load OSS license data
        List<String[]> deps = loadOssLicenses();

        String[] columns = {"Bibliothek", "Version", "Lizenz"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        for (String[] dep : deps) {
            model.addRow(dep);
        }

        JTable table = new JTable(model);
        table.setFillsViewportHeight(true);
        table.setRowHeight(22);
        table.getTableHeader().setReorderingAllowed(false);

        // Column widths
        table.getColumnModel().getColumn(0).setPreferredWidth(280);
        table.getColumnModel().getColumn(1).setPreferredWidth(80);
        table.getColumnModel().getColumn(2).setPreferredWidth(180);

        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setPreferredSize(new Dimension(600, 220));
        ossPanel.add(tableScroll, BorderLayout.CENTER);

        // Count label at bottom
        JLabel countLabel = new JLabel("  " + deps.size() + " Bibliothek(en)");
        countLabel.setFont(countLabel.getFont().deriveFont(10f));
        ossPanel.add(countLabel, BorderLayout.SOUTH);

        return ossPanel;
    }

    /**
     * Loads oss-licenses.json from classpath (generated by Gradle at build time).
     * Falls back gracefully if the file is missing.
     */
    private List<String[]> loadOssLicenses() {
        List<String[]> result = new ArrayList<>();
        try (InputStream in = getClass().getResourceAsStream("/oss-licenses.json")) {
            if (in == null) {
                LOG.warning("[About] oss-licenses.json not found on classpath – OSS list will be empty.");
                result.add(new String[]{"(nicht verfügbar)", "", "Build ohne Lizenzreport"});
                return result;
            }
            // Simple manual JSON array parsing to avoid adding a dependency for the About dialog
            // Format: [{"group":"...","artifact":"...","version":"...","license":"..."}, ...]
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
            }
            String json = sb.toString().trim();
            // Parse using simple string operations (the JSON is well-known, build-generated)
            result = parseOssJson(json);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[About] Failed to load OSS licenses", e);
            result.add(new String[]{"(Fehler beim Laden)", "", e.getMessage()});
        }
        return result;
    }

    /**
     * Minimal JSON array parser for the well-known oss-licenses.json format.
     * Avoids importing Gson just for the About dialog.
     */
    private List<String[]> parseOssJson(String json) {
        List<String[]> result = new ArrayList<>();
        // Remove outer brackets
        json = json.trim();
        if (json.startsWith("[")) json = json.substring(1);
        if (json.endsWith("]")) json = json.substring(0, json.length() - 1);

        // Split by "},{" pattern
        String[] objects = json.split("\\}\\s*,\\s*\\{");
        for (String obj : objects) {
            obj = obj.replace("{", "").replace("}", "").trim();
            if (obj.isEmpty()) continue;

            String group = extractJsonValue(obj, "group");
            String artifact = extractJsonValue(obj, "artifact");
            String version = extractJsonValue(obj, "version");
            String license = extractJsonValue(obj, "license");

            String name = (group != null ? group : "") + ":" + (artifact != null ? artifact : "");
            result.add(new String[]{name, version != null ? version : "", license != null ? license : "Unknown"});
        }
        return result;
    }

    private String extractJsonValue(String obj, String key) {
        String pattern = "\"" + key + "\"";
        int idx = obj.indexOf(pattern);
        if (idx < 0) return null;
        int colonIdx = obj.indexOf(':', idx + pattern.length());
        if (colonIdx < 0) return null;
        // Find value start (skip whitespace and opening quote)
        int start = colonIdx + 1;
        while (start < obj.length() && (obj.charAt(start) == ' ' || obj.charAt(start) == '\t')) start++;
        if (start >= obj.length()) return null;
        if (obj.charAt(start) == '"') {
            start++;
            int end = obj.indexOf('"', start);
            if (end < 0) return obj.substring(start);
            return obj.substring(start, end);
        }
        // Non-quoted value (number, boolean, null)
        int end = start;
        while (end < obj.length() && obj.charAt(end) != ',' && obj.charAt(end) != '}') end++;
        return obj.substring(start, end).trim();
    }
}
