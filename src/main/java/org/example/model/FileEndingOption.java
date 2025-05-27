package org.example.model;

import javax.swing.*;
import java.util.LinkedHashMap;
import java.util.Map;

public class FileEndingOption {
    public static final Map<String, String> PRESETS = new LinkedHashMap<>();

    static {
        PRESETS.put("Keine", "");
        PRESETS.put("FF (Form Feed)", "0C");
        PRESETS.put("FF02", "FF02");
        PRESETS.put("FFFF", "FFFF");
        PRESETS.put("EOF", "1A");
    }

    /**
     * Erzeugt eine editierbare Kombobox mit Vorschlagswerten für Datei-Endemarkierungen
     */
    public static JComboBox<String> createEndMarkerComboBox(String currentHexValue) {
        JComboBox<String> comboBox = new JComboBox<>();
        comboBox.setEditable(true);

        for (Map.Entry<String, String> entry : PRESETS.entrySet()) {
            comboBox.addItem(entry.getValue());
        }

        comboBox.setSelectedItem(currentHexValue != null ? currentHexValue : "");
        return comboBox;
    }

    /**
     * Normalisiert Benutzereingabe (z. B. entfernt Leerzeichen, wandelt in Großbuchstaben um)
     */
    public static String normalizeInput(Object selectedItem) {
        if (selectedItem == null) return "";
        String raw = selectedItem.toString().trim();
        return raw.replaceAll("[^0-9A-Fa-f]", "").toUpperCase();
    }
}
