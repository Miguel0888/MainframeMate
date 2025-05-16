package org.example.ui;

import org.example.ftp.FtpManager;
import org.example.model.Settings;
import org.example.util.SettingsManager;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;

public class SettingsDialog {

    public static void show(Component parent, FtpManager ftpManager) {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = createDefaultGbc();

        JCheckBox utf8Check = createDisabledCheckBox("UTF-8 Unterstützung");
        JCheckBox featCheck = createDisabledCheckBox("FEAT-Befehl verfügbar");
        JButton detailsButton = new JButton("Details...");
        detailsButton.setEnabled(false);

        Map<String, Set<String>> featureMap = new LinkedHashMap<>();
        String featureText = loadAndParseFeatures(ftpManager, featureMap);

        if (!featureMap.isEmpty()) {
            featCheck.setSelected(true);
            detailsButton.setEnabled(true);
        }
        if (featureMap.containsKey("UTF8")) {
            utf8Check.setSelected(true);
        }

        JComboBox<String> encodingCombo = new JComboBox<>();
        getEncodingOptions().forEach(encodingCombo::addItem);

        Settings settings = SettingsManager.load();
        String currentEncoding = settings.encoding != null ? settings.encoding : "ISO-8859-1";
        encodingCombo.setSelectedItem(currentEncoding);

        addEncodingSelector(panel, gbc, encodingCombo);
        addFeatureIndicators(panel, gbc, featCheck, utf8Check);
        addFeatureDetailsButton(panel, gbc, detailsButton, parent, featureText);

        int result = JOptionPane.showConfirmDialog(parent, panel, "Einstellungen",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String selectedEncoding = (String) encodingCombo.getSelectedItem();
            settings.encoding = selectedEncoding; // Save to settings
            SettingsManager.save(settings); // Persist setting

            ftpManager.getClient().setControlEncoding(selectedEncoding);

            JOptionPane.showMessageDialog(parent,
                    "Kodierung gesetzt auf: " + selectedEncoding,
                    "Info", JOptionPane.INFORMATION_MESSAGE);
        }

    }

    private static GridBagConstraints createDefaultGbc() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;
        return gbc;
    }

    private static JCheckBox createDisabledCheckBox(String label) {
        JCheckBox box = new JCheckBox(label);
        box.setEnabled(false);
        return box;
    }

    private static void addEncodingSelector(JPanel panel, GridBagConstraints gbc, JComboBox<String> encodingCombo) {
        gbc.gridwidth = 2;
        panel.add(new JLabel("Zeichenkodierung:"), gbc);
        gbc.gridy++;
        panel.add(encodingCombo, gbc);
        gbc.gridy++;
        gbc.gridwidth = 1;
    }

    private static void addFeatureIndicators(JPanel panel, GridBagConstraints gbc, JCheckBox featCheck, JCheckBox utf8Check) {
        panel.add(featCheck, gbc);
        gbc.gridx = 1;
        panel.add(utf8Check, gbc);
        gbc.gridx = 0;
        gbc.gridy++;
    }

    private static void addFeatureDetailsButton(JPanel panel, GridBagConstraints gbc, JButton button,
                                                Component parent, String featureText) {
        button.addActionListener(e -> {
            JTextArea area = new JTextArea(featureText);
            area.setEditable(false);
            area.setLineWrap(true);
            area.setWrapStyleWord(true);

            JScrollPane scroll = new JScrollPane(area);
            scroll.setPreferredSize(new Dimension(500, 300));

            JOptionPane.showMessageDialog(parent, scroll, "Server-Features", JOptionPane.INFORMATION_MESSAGE);
        });
        panel.add(button, gbc);
    }

    private static void applySelectedEncoding(FtpManager ftpManager, Component parent, JComboBox<String> combo) {
        String selected = (String) combo.getSelectedItem();
        ftpManager.getClient().setControlEncoding(selected);
        JOptionPane.showMessageDialog(parent, "Kodierung gesetzt auf: " + selected);
    }

    private static List<String> getEncodingOptions() {
        List<String> encodings = new ArrayList<>();
        encodings.add("UTF-8");
        encodings.add("ISO-8859-1");
        encodings.add("Cp1047"); // EBCDIC (Latin-1)
        encodings.add("Cp037");  // EBCDIC US/Canada
        encodings.add("IBM01140"); // weitere EBCDIC Varianten
        return encodings;
    }

    private static String loadAndParseFeatures(FtpManager ftpManager, Map<String, Set<String>> featureMap) {
        try {
            int code = ftpManager.getClient().sendCommand("FEAT");
            if (code != 211 && code != 214) return "(FEAT nicht unterstützt)";

            String[] lines = ftpManager.getClient().getReplyStrings();
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || Character.isDigit(line.charAt(0))) continue;

                String[] parts = line.split(" ", 2);
                String key = parts[0].toUpperCase();
                String value = parts.length > 1 ? parts[1].trim() : "";

                featureMap.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(value);
            }
            return String.join("\n", lines);
        } catch (IOException e) {
            return "Fehler beim Laden der Features: " + e.getMessage();
        }
    }

}
