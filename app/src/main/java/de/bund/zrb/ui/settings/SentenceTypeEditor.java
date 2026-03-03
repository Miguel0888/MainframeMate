package de.bund.zrb.ui.settings;

import de.bund.zrb.ui.settings.pojo.FieldTableModel;
import de.bund.zrb.ui.settings.pojo.PathsTableModel;
import de.zrb.bund.newApi.sentence.FieldCoordinate;
import de.zrb.bund.newApi.sentence.SentenceDefinition;
import de.zrb.bund.newApi.sentence.SentenceField;
import de.zrb.bund.newApi.sentence.SentenceMeta;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Set;

public class SentenceTypeEditor extends JPanel {

    private final JTextField nameField = new JTextField();
    private final PathsTableModel pathsModel = new PathsTableModel();
    private final JTable pathsTable = new JTable(pathsModel);
    private final JTextField pathPatternField = new JTextField();
    private final JCheckBox appendCheckbox = new JCheckBox("An Inhalt anhängen");

    private final JTextField extensionsField = new JTextField();
    private final JComboBox<String> transferModeCombo = new JComboBox<>(new String[]{"ASCII", "Binary"});
    private final JComboBox<String> syntaxCombo = new JComboBox<>(new String[]{
            "", "JCL", "COBOL", "NATURAL",
            "Java", "Python", "JavaScript", "TypeScript", "JSON", "XML", "HTML", "CSS", "SQL",
            "Markdown", "YAML", "Properties", "Shell", "Batch", "Groovy", "Kotlin", "Scala",
            "C", "C++", "Go", "Rust", "Ruby", "PHP", "Perl", "Lua"
    });

    private final FieldTableModel fieldModel = new FieldTableModel();
    private final JTable fieldTable = new JTable(fieldModel);
    public String originalKey = ""; // wird extern gesetzt

    private boolean fileTypeMode = false;
    private JScrollPane fieldScrollPane;
    private JPanel fieldButtonPanel;
    private JLabel extensionsLabel;
    private JLabel transferLabel;
    private JLabel syntaxLabel;

    /**
     * Switches the editor between sentence type mode and file type mode.
     * In file type mode, the field table is hidden and extensions/transfer fields are shown.
     */
    public void setFileTypeMode(boolean fileType) {
        this.fileTypeMode = fileType;
        if (fieldScrollPane != null) fieldScrollPane.setVisible(!fileType);
        if (fieldButtonPanel != null) fieldButtonPanel.setVisible(!fileType);
        if (extensionsLabel != null) extensionsLabel.setVisible(fileType);
        if (extensionsField != null) extensionsField.setVisible(fileType);
        if (transferLabel != null) transferLabel.setVisible(fileType);
        if (transferModeCombo != null) transferModeCombo.setVisible(fileType);
        if (syntaxLabel != null) syntaxLabel.setVisible(fileType);
        if (syntaxCombo != null) syntaxCombo.setVisible(fileType);
        appendCheckbox.setVisible(!fileType);
    }

    public SentenceTypeEditor() {
        setLayout(new BorderLayout(8, 8));
        setBorder(new EmptyBorder(8, 8, 8, 8));

        pathsTable.setToolTipText(
                "<html>Liste fixer Pfade, je einer pro Zeile, z.B.:<br>" +
                        "<code>DATA.SA100</code><br>" +
                        "Optional neben Pfad-Pattern verwendbar.</html>"
        );

        pathPatternField.setToolTipText(
                "<html>Regulärer Ausdruck für den Datei-Pfad, z. B.:<br>" +
                        "<code>DATA\\.SA1\\d{2}</code><br>" +
                        "Hinweis: Doppelte Backslashes (\\) erforderlich für Punkte etc.</html>"
        );

        extensionsField.setToolTipText(
                "<html>Dateiendungen (ohne Punkt), kommagetrennt, z.B.:<br>" +
                        "<code>pdf</code> oder <code>doc, docx</code></html>"
        );

        fieldButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JPanel metaPanel = createMetaPanel(fieldButtonPanel);

        fieldScrollPane = new JScrollPane(fieldTable);

        add(metaPanel, BorderLayout.NORTH);
        add(fieldScrollPane, BorderLayout.CENTER);
        add(fieldButtonPanel, BorderLayout.SOUTH);
    }

    private @NotNull JPanel createMetaPanel(JPanel buttonPanel) {
        JPanel metaPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;

        // Satzart-Name
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        metaPanel.add(new JLabel("Satzart-Name"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        metaPanel.add(nameField, gbc);

        // Pfade
        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        metaPanel.add(new JLabel("Pfade"), gbc);

        pathsTable.setFillsViewportHeight(true);
        JScrollPane scrollPane = new JScrollPane(pathsTable);
        scrollPane.setPreferredSize(new Dimension(100, 80));

        gbc.gridx = 1;
        gbc.weightx = 1;
        // Panel mit Tabelle + Buttons rechts daneben
        JPanel pathsPanel = new JPanel(new BorderLayout(4, 0));
        pathsPanel.add(scrollPane, BorderLayout.CENTER);

        Box buttonBox = Box.createVerticalBox();
        buttonBox.add(Box.createVerticalStrut(2)); // optional spacing
        JButton addPathButton = new JButton("➕");
        addPathButton.setToolTipText("Pfad hinzufügen");
        addPathButton.addActionListener(e -> pathsModel.addPath(""));
        buttonBox.add(addPathButton);

        JButton removePathButton = new JButton("❌");
        removePathButton.setToolTipText("Ausgewählten Pfad entfernen");
        removePathButton.addActionListener(e -> {
            int r = pathsTable.getSelectedRow();
            if (r >= 0) {
                pathsModel.removePath(r);
            }
        });
        buttonBox.add(Box.createVerticalStrut(4)); // spacing
        buttonBox.add(removePathButton);

        pathsPanel.add(buttonBox, BorderLayout.EAST);

        // nun das ganze Panel in die GridBag-Zelle legen
        gbc.gridx = 1;
        gbc.weightx = 1;
        metaPanel.add(pathsPanel, gbc);


        // Pfad-Pattern (Regex)
        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        metaPanel.add(new JLabel("Pfad-Pattern (RegEx)"), gbc);

        pathPatternField.setToolTipText(
                "<html>Regulärer Ausdruck für Datei-Pfade, z. B.:<br>" +
                        "<code>DATA\\.SA1\\d{2}</code><br>" +
                        "Hinweis: Doppelte Backslashes (\\) erforderlich</html>"
        );

        gbc.gridx = 1;
        gbc.weightx = 1;
        metaPanel.add(pathPatternField, gbc);

        // Checkbox
        row++;
        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.WEST;
        metaPanel.add(appendCheckbox, gbc);

        // Endungen (für Dateitypen)
        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        extensionsLabel = new JLabel("Endungen");
        extensionsLabel.setVisible(false);
        metaPanel.add(extensionsLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        extensionsField.setVisible(false);
        metaPanel.add(extensionsField, gbc);

        // Transfer-Modus (für Dateitypen)
        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        transferLabel = new JLabel("Transfer-Modus");
        transferLabel.setVisible(false);
        metaPanel.add(transferLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        transferModeCombo.setSelectedIndex(0); // ASCII default
        transferModeCombo.setVisible(false);
        transferModeCombo.setToolTipText(
                "<html>ASCII: Textdateien (JCL, COBOL, MD, ...)<br>" +
                        "Binary: Binärdateien (PDF, WORD, EXCEL, ...)</html>"
        );
        metaPanel.add(transferModeCombo, gbc);

        // Syntax-Highlighting (für Dateitypen / Programmiersprachen)
        row++;
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        syntaxLabel = new JLabel("Syntax-Highlighting");
        syntaxLabel.setVisible(false);
        metaPanel.add(syntaxLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        syntaxCombo.setVisible(false);
        syntaxCombo.setEditable(true); // allow custom syntax styles
        syntaxCombo.setToolTipText(
                "<html>Syntax-Highlighting für Programmiersprachen.<br>" +
                        "Leer = kein Highlighting (z.B. für PDF, WORD).<br>" +
                        "Wählen Sie eine Sprache oder geben Sie einen eigenen RSyntaxTextArea-Style ein.</html>"
        );
        metaPanel.add(syntaxCombo, gbc);

        // Buttons für Felder
        JButton addFieldButton = new JButton("➕ Feld");
        addFieldButton.addActionListener(e -> {
            SentenceField newField = new SentenceField();
            FieldCoordinate coord = generateFreeCoordinate();
            fieldModel.addField(coord, newField);
        });

        JButton removeFieldButton = new JButton("❌ Entfernen");
        removeFieldButton.addActionListener(e -> {
            int selected = fieldTable.getSelectedRow();
            if (selected >= 0) {
                fieldModel.removeField(selected);
            }
        });

        JButton colorButton = new JButton("🎨 Farbe wählen");
        colorButton.addActionListener(e -> {
            int selected = fieldTable.getSelectedRow();
            if (selected >= 0) {
                SentenceField f = fieldModel.getFieldAt(selected);
                String colorValue = f.getColor();
                if (colorValue == null || colorValue.trim().isEmpty()) {
                    colorValue = "#000000";
                }
                Color c = JColorChooser.showDialog(this, "Feldfarbe wählen", Color.decode(colorValue));
                if (c != null) {
                    String hex = String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
                    f.setColor(hex);
                    fieldModel.fireTableRowsUpdated(selected, selected);
                }
            }
        });

        buttonPanel.add(addFieldButton);
        buttonPanel.add(removeFieldButton);
        buttonPanel.add(colorButton);

        // Tooltip für Spalte „Schema“
        fieldTable.setFillsViewportHeight(true);
        fieldTable.getTableHeader().addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(java.awt.event.MouseEvent e) {
                int col = fieldTable.columnAtPoint(e.getPoint());
                if (col == 4) {
                    fieldTable.getTableHeader().setToolTipText(
                            "<html>Regulärer Ausdruck für gültige Feldwerte, z. B.:<br>" +
                                    "<code>[0-9]{6}</code> für Datum TTMMJJ</html>"
                    );
                } else {
                    fieldTable.getTableHeader().setToolTipText(null);
                }
            }
        });

        return metaPanel;
    }


    private JPanel createLabeled(String label, JComponent component) {
        JPanel panel = new JPanel(new BorderLayout(4, 0));
        panel.add(new JLabel(label), BorderLayout.WEST);
        panel.add(component, BorderLayout.CENTER);
        return panel;
    }

    /**
     * Creates a SentenceDefinition from the current editor state.
     * Validates the input and returns null if there are errors.
     *
     * Method is required to save the sentence typSchluessel configuration.
     */
    public SentenceDefinition getDefinition() {
        SentenceDefinition def = new SentenceDefinition();

        SentenceMeta meta = new SentenceMeta();
        meta.setPaths(pathsModel.getPaths());
        meta.setPathPattern(pathPatternField.getText().trim());
        meta.setAppend(appendCheckbox.isSelected());

        // Extensions (for file types)
        String extText = extensionsField.getText().trim();
        if (!extText.isEmpty()) {
            java.util.List<String> exts = new java.util.ArrayList<>();
            for (String ext : extText.split("[,;\\s]+")) {
                String cleaned = ext.trim().toLowerCase();
                if (cleaned.startsWith(".")) cleaned = cleaned.substring(1);
                if (!cleaned.isEmpty()) exts.add(cleaned);
            }
            meta.setExtensions(exts);
        }

        // Transfer mode (for file types)
        if (fileTypeMode) {
            String selected = (String) transferModeCombo.getSelectedItem();
            meta.setTransferMode("Binary".equals(selected) ? "binary" : "ascii");

            // Syntax style (for programming language types)
            Object syntaxVal = syntaxCombo.getSelectedItem();
            if (syntaxVal != null && !syntaxVal.toString().trim().isEmpty()) {
                meta.setSyntaxStyle(resolveSyntaxStyle(syntaxVal.toString().trim()));
            }
        }

        def.setMeta(meta);
        def.setFields(fieldModel.getInternalMap());

        if (!sanitizeAndCheck(def)) return null;

        return def;
    }

    private boolean sanitizeAndCheck(SentenceDefinition def) {
        for (SentenceField f : def.getFields().values()) {
            // Set default color if empty or null
            if (f.getColor() == null || f.getColor().trim().isEmpty()) {
                f.setColor("#FFFFFF");
            }

            // Validate value pattern if set
            String regex = f.getValuePattern();
            if (regex != null && !regex.trim().isEmpty()) {
                try {
                    java.util.regex.Pattern.compile(regex);
                } catch (java.util.regex.PatternSyntaxException e) {
                    JOptionPane.showMessageDialog(this,
                            "Ungültiger regulärer Ausdruck in Feld '" + f.getName() + "':\n" + regex,
                            "Regex-Fehler", JOptionPane.ERROR_MESSAGE);
                    return false;
                }
            }
        }
        return true;
    }

    public String getKey() {
        return nameField.getText().trim();
    }

    /**
     * Sets the editor fields with the given sentence typSchluessel data.
     * This is used to load existing sentence definitions for editing.
     *
     * @param name The name of the sentence typSchluessel
     * @param def The SentenceDefinition containing the fields and metadata
     */
    public void setData(String name, SentenceDefinition def) {
        nameField.setText(name);
        originalKey = name;

        if (def.getMeta() != null) {
            pathPatternField.setText(def.getMeta().getPathPattern());
            appendCheckbox.setSelected(Boolean.TRUE.equals(def.getMeta().isAppend()));
            pathsModel.setPaths(def.getMeta().getPaths());

            // Extensions
            if (def.getMeta().getExtensions() != null && !def.getMeta().getExtensions().isEmpty()) {
                extensionsField.setText(String.join(", ", def.getMeta().getExtensions()));
            }

            // Transfer mode
            if (def.getMeta().isBinaryTransfer()) {
                transferModeCombo.setSelectedItem("Binary");
            } else {
                transferModeCombo.setSelectedItem("ASCII");
            }

            // Syntax style
            if (def.getMeta().hasSyntaxStyle()) {
                syntaxCombo.setSelectedItem(displaySyntaxStyle(def.getMeta().getSyntaxStyle()));
            } else {
                syntaxCombo.setSelectedIndex(0);
            }
        }

        fieldModel.setFields(def.getFields());
    }

    /**
     * Maps a user-friendly display name to an RSyntaxTextArea syntax constant.
     */
    private static String resolveSyntaxStyle(String displayName) {
        if (displayName == null || displayName.isEmpty()) return null;
        switch (displayName.toUpperCase()) {
            case "JCL":        return "text/properties"; // best approximation
            case "COBOL":      return "text/plain";
            case "NATURAL":    return de.bund.zrb.ui.syntax.MainframeSyntaxSupport.SYNTAX_STYLE_NATURAL;
            case "JAVA":       return org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_JAVA;
            case "PYTHON":     return org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_PYTHON;
            case "JAVASCRIPT": return org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT;
            case "TYPESCRIPT": return org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_TYPESCRIPT;
            case "JSON":       return org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_JSON;
            case "XML":        return org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_XML;
            case "HTML":       return org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_HTML;
            case "CSS":        return org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_CSS;
            case "SQL":        return org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_SQL;
            case "MARKDOWN":   return org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_MARKDOWN;
            case "YAML":       return org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_YAML;
            case "PROPERTIES": return org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_PROPERTIES_FILE;
            case "SHELL":      return org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL;
            case "BATCH":      return org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_WINDOWS_BATCH;
            case "GROOVY":     return org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_GROOVY;
            case "KOTLIN":     return org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_KOTLIN;
            case "SCALA":      return org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_SCALA;
            case "C":          return org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_C;
            case "C++":        return org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS;
            case "GO":         return org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_GO;
            case "RUST":       return org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_NONE; // no native Rust support
            case "RUBY":       return org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_RUBY;
            case "PHP":        return org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_PHP;
            case "PERL":       return org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_PERL;
            case "LUA":        return org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_LUA;
            default:           return displayName; // allow raw RSyntaxTextArea style strings
        }
    }

    /**
     * Maps an RSyntaxTextArea syntax constant back to a user-friendly display name.
     */
    private static String displaySyntaxStyle(String syntaxStyle) {
        if (syntaxStyle == null || syntaxStyle.isEmpty()) return "";
        // Check known mappings in reverse
        if (syntaxStyle.equals(de.bund.zrb.ui.syntax.MainframeSyntaxSupport.SYNTAX_STYLE_NATURAL)) return "NATURAL";
        if (syntaxStyle.equals("text/properties")) return "JCL";
        if (syntaxStyle.equals(org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_JAVA)) return "Java";
        if (syntaxStyle.equals(org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_PYTHON)) return "Python";
        if (syntaxStyle.equals(org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT)) return "JavaScript";
        if (syntaxStyle.equals(org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_TYPESCRIPT)) return "TypeScript";
        if (syntaxStyle.equals(org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_JSON)) return "JSON";
        if (syntaxStyle.equals(org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_XML)) return "XML";
        if (syntaxStyle.equals(org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_HTML)) return "HTML";
        if (syntaxStyle.equals(org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_CSS)) return "CSS";
        if (syntaxStyle.equals(org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_SQL)) return "SQL";
        if (syntaxStyle.equals(org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_MARKDOWN)) return "Markdown";
        if (syntaxStyle.equals(org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_YAML)) return "YAML";
        // Fallback: return raw style
        return syntaxStyle;
    }

    private FieldCoordinate generateFreeCoordinate() {
        Set<FieldCoordinate> used = fieldModel.getInternalMap().keySet();
        for (int row = 1; row <= 10; row++) {
            for (int pos = 1; pos <= 999; pos++) {
                FieldCoordinate coord = new FieldCoordinate(row, pos);
                if (!used.contains(coord)) {
                    return coord;
                }
            }
        }
        throw new IllegalStateException("No free coordinate found");
    }



}
