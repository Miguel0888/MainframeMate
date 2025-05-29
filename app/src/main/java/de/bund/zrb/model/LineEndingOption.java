package de.bund.zrb.model;

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

    public static String normalizeInput(Object selectedItem) {
        if (selectedItem == null) return "";
        String raw = selectedItem.toString().trim();
        return raw.replaceAll("[^0-9A-Fa-f]", "").toUpperCase();
    }
}
