package de.bund.zrb.ui.settings;

import javax.swing.*;
import java.awt.*;

public class ExpressionEditorDialog {

    public static void show(Component parent) {
        ExpressionEditorPanel panel = new ExpressionEditorPanel();

        int result = JOptionPane.showConfirmDialog(
                parent,
                panel,
                "Dynamische Ausdrücke verwalten",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        if (result == JOptionPane.OK_OPTION) {
            panel.saveChanges();
        }
    }
}
