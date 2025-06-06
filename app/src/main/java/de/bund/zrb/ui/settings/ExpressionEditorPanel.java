package de.bund.zrb.ui.settings;

import com.google.googlejavaformat.java.Formatter;
import com.google.googlejavaformat.java.FormatterException;
import de.bund.zrb.runtime.ExpressionRegistryImpl;
import de.zrb.bund.api.ExpressionRegistry;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.Callable;

/**
 * Panel zur Verwaltung von dynamischen Java-Ausdrücken mit Syntax-Highlighting.
 */
public class ExpressionEditorPanel extends JPanel {

    private final RSyntaxTextArea codeArea = new RSyntaxTextArea(20, 60);
    private final JTextArea resultArea = new JTextArea(5, 40);
    private final JComboBox<String> keyDropdown = new JComboBox<>();
    private final ExpressionRegistry registry = ExpressionRegistryImpl.getInstance();

    public ExpressionEditorPanel() {
        setLayout(new BorderLayout(10, 10));

        keyDropdown.addItem(""); // leerer Eintrag

        // Beispiele initial registrieren
        ExpressionExamples.getExamples().forEach(registry::register);

        for (String key : registry.getKeys()) {
            keyDropdown.addItem(key);
        }

        codeArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
        codeArea.setCodeFoldingEnabled(true);
        codeArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        resultArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        resultArea.setEditable(false);

        keyDropdown.addActionListener(e -> {
            String key = (String) keyDropdown.getSelectedItem();
            if (key != null && !key.trim().isEmpty()) {
                codeArea.setText(formatForEditor(key));
            } else {
                codeArea.setText("");
            }
        });

        JPanel top = new JPanel(new BorderLayout());
        top.add(keyDropdown, BorderLayout.NORTH);
        top.add(new RTextScrollPane(codeArea), BorderLayout.CENTER);

        JButton evalButton = new JButton("▶ Ausführen");
        JButton saveButton = new JButton("💾 Speichern unter...");
        JButton removeButton = new JButton("❌ Entfernen");
        JButton formatButton = new JButton("✨ Formatieren");

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(evalButton);
        buttons.add(saveButton);
        buttons.add(removeButton);
        buttons.add(formatButton);

        evalButton.addActionListener(e -> evaluate());
        saveButton.addActionListener(e -> saveAsNew());
        removeButton.addActionListener(e -> removeCurrent());

        formatButton.addActionListener(e -> {
            try {
                String formatted = new Formatter().formatSource(codeArea.getText());
                codeArea.setText(formatted);
            } catch (FormatterException ex) {
                JOptionPane.showMessageDialog(this, "Fehler beim Formatieren:\n" + ex.getMessage(), "Formatierfehler", JOptionPane.ERROR_MESSAGE);
            }
        });

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(buttons, BorderLayout.NORTH);
        bottom.add(new JScrollPane(resultArea), BorderLayout.CENTER);

        add(top, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        // initial
        keyDropdown.setSelectedItem("");
        codeArea.setText("");
    }

    private String formatForEditor(String key) {
        return registry.getCode(key).orElse("");
    }

    private void evaluate() {
        try {
            String key = (String) keyDropdown.getSelectedItem();
            if (key != null && !key.trim().isEmpty()) {
                String result = registry.evaluate(key);
                resultArea.setText(result);
            } else {
                resultArea.setText("Kein Ausdruck ausgewählt.");
            }
        } catch (Exception ex) {
            resultArea.setText("Fehler: " + ex.getMessage());
        }
    }

    private void saveAsNew() {
        String newKey = JOptionPane.showInputDialog(this, "Name für neuen Ausdruck:");
        if (newKey != null && !newKey.trim().isEmpty()) {
            registry.register(newKey, codeArea.getText());

            ExpressionRegistryImpl.getInstance().save();

            keyDropdown.addItem(newKey);
            keyDropdown.setSelectedItem(newKey);
        }
    }

    private void removeCurrent() {
        String key = (String) keyDropdown.getSelectedItem();
        if (key != null && !key.trim().isEmpty()) {
            registry.remove(key);

            ExpressionRegistryImpl.getInstance().save();

            keyDropdown.removeItem(key);
            keyDropdown.setSelectedItem("");
            codeArea.setText("");
            resultArea.setText("");
        }
    }


    public void saveChanges() {
        ExpressionRegistryImpl.getInstance().save();
    }
}
