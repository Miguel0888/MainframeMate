package de.bund.zrb.ui.util;

import javax.swing.*;
import java.awt.*;

public class UnicodeFontFix {
    public static void apply() {
        // Setze globale Standardschriftart auf Segoe UI Emoji
        Font unicodeFont = new Font("Segoe UI Emoji", Font.PLAIN, 12);

        UIManager.put("Label.font", unicodeFont);
        UIManager.put("Button.font", unicodeFont);
        UIManager.put("TextField.font", unicodeFont);
        UIManager.put("TextArea.font", unicodeFont);
        UIManager.put("CheckBox.font", unicodeFont);
        UIManager.put("RadioButton.font", unicodeFont);
        UIManager.put("ComboBox.font", unicodeFont);
        UIManager.put("List.font", unicodeFont);
        UIManager.put("Table.font", unicodeFont);
        UIManager.put("Tree.font", unicodeFont);
        // ... ggf. weitere Komponenten

        // Optional: Look & Feel neu laden
        for (Window window : Window.getWindows()) {
            SwingUtilities.updateComponentTreeUI(window);
        }
    }
}
