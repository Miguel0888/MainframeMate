package de.bund.zrb.model;

import javax.swing.*;
import java.util.LinkedHashMap;
import java.util.Map;

public class PaddingOption {
    public static final Map<String, String> PRESETS = new LinkedHashMap<>();

    static {
        PRESETS.put("Keine", "");
        PRESETS.put("00 (NULL)", "00");
    }

    /**
     * Erzeugt eine editierbare Kombobox mit Vorschlagswerten für Datei-Endemarkierungen
     */
    public static JComboBox<String> createPaddingComboBox(String currentHexValue) {
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

        // Nur gültige Hex-Zeichen, in Großbuchstaben
        String hex = raw.replaceAll("[^0-9A-Fa-f]", "").toUpperCase();

        // Auf genau zwei Zeichen beschränken (ein Byte)
        if (hex.length() > 2) {
            hex = hex.substring(0, 2);
        }

        return hex;
    }
}
