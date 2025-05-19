package org.example.model;

import org.example.util.SettingsManager;

import javax.swing.*;
import java.util.LinkedHashMap;
import java.util.Map;

public class LineEndingOption {
    public static final Map<String, String> PRESETS = new LinkedHashMap<>();

    static {
        PRESETS.put("LF (\\n)", "0A");
        PRESETS.put("CRLF (\\r\\n)", "0D0A");
        PRESETS.put("FF 01", "FF01");
        PRESETS.put("Keine (Mainframe)", "");
    }

    public static JComboBox<String> createLineEndingComboBox(String currentHexValue) {
        JComboBox<String> comboBox = new JComboBox<>();
        comboBox.setEditable(true);

        for (Map.Entry<String, String> entry : PRESETS.entrySet()) {
            comboBox.addItem(entry.getValue());
        }

        comboBox.setSelectedItem(currentHexValue != null ? currentHexValue : "FF01");
        return comboBox;
    }

    public static String resolveDisplayValue(String input) {
        return PRESETS.entrySet().stream()
                .filter(e -> e.getValue().equalsIgnoreCase(input))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(input);
    }

    public static String normalizeInput(Object selectedItem) {
        if (selectedItem == null) return "";
        String raw = selectedItem.toString().trim();
        return raw.replaceAll("[^0-9A-Fa-f]", "").toUpperCase();
    }

    public static String getEffectiveLineEnding() {
        return normalizeInput(SettingsManager.load().lineEnding);
    }

    public static byte[] getLineEndingBytes() {
        String hex = getEffectiveLineEnding();
        int len = hex.length();
        if (len == 0 || len % 2 != 0) return new byte[0];

        byte[] result = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            result[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
        }
        return result;
    }
}
