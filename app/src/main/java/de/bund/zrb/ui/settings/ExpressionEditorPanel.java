package de.bund.zrb.ui.settings;

import com.google.googlejavaformat.java.Formatter;
import com.google.googlejavaformat.java.FormatterException;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import de.bund.zrb.runtime.ExpressionRegistryImpl;
import de.zrb.bund.api.ExpressionRegistry;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ExpressionEditorPanel extends JPanel {

    private final RSyntaxTextArea codeArea = new RSyntaxTextArea(20, 60);
    private final JTextArea resultArea = new JTextArea(5, 40);
    private final JComboBox<String> keyDropdown = new JComboBox<>();
    private final JTextField paramInput = new JTextField(); // Neues Eingabefeld
    private final ExpressionRegistry registry = ExpressionRegistryImpl.getInstance();

    public ExpressionEditorPanel() {
        setLayout(new BorderLayout(10, 10));

        keyDropdown.addItem(""); // leerer Eintrag

        ExpressionExamples.ensureExamplesRegistered(registry);
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
            codeArea.setText((key != null && !key.trim().isEmpty()) ? formatForEditor(key) : "");
        });

        // Editor oben
        JPanel top = new JPanel(new BorderLayout());
        top.add(keyDropdown, BorderLayout.NORTH);
        top.add(new RTextScrollPane(codeArea), BorderLayout.CENTER);

        // Parameterfeld
        JPanel paramPanel = new JPanel(new BorderLayout(5, 5));
        paramPanel.add(new JLabel("Parameter (z.B. [\"2024-01-01\"] oder CSV):"), BorderLayout.NORTH);
        paramPanel.add(paramInput, BorderLayout.CENTER);

        // Buttons
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

        // Ergebnisanzeige
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(buttons, BorderLayout.NORTH);
        bottom.add(new JScrollPane(resultArea), BorderLayout.CENTER);

        // Hauptlayout
        add(top, BorderLayout.NORTH);
        add(paramPanel, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        // Initialzustand
        keyDropdown.setSelectedItem("");
        codeArea.setText("");
    }

    private String formatForEditor(String key) {
        return registry.getCode(key).orElse("");
    }

    private void evaluate() {
        try {
            String key = (String) keyDropdown.getSelectedItem();
            if (key == null || key.trim().isEmpty()) {
                resultArea.setText("Kein Ausdruck ausgewählt.");
                return;
            }

            List<String> params = parseParameters(paramInput.getText());
            String result = registry.evaluate(key, params);
            resultArea.setText(result);
        } catch (Exception ex) {
            resultArea.setText("Fehler: " + ex.getMessage());
        }
    }

    private List<String> parseParameters(String input) {
        if (input == null || input.trim().isEmpty()) {
            return Collections.emptyList();
        }

        try {
            Type listType = new TypeToken<List<String>>() {}.getType();
            return new Gson().fromJson(input, listType);
        } catch (Exception e) {
            return Arrays.asList(input.split("\\s*,\\s*"));
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
