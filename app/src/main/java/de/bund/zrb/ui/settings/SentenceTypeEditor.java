package de.bund.zrb.ui.settings;

import de.bund.zrb.ui.settings.pojo.FieldTableModel;
import de.bund.zrb.ui.settings.pojo.PathsTableModel;
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

    private final FieldTableModel fieldModel = new FieldTableModel();
    private final JTable fieldTable = new JTable(fieldModel);
    public String originalKey = ""; // wird extern gesetzt

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

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JPanel metaPanel = createMetaPanel(buttonPanel);

        add(metaPanel, BorderLayout.NORTH);
        add(new JScrollPane(fieldTable), BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
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

        // Buttons für Felder
        JButton addFieldButton = new JButton("➕ Feld");
        addFieldButton.addActionListener(e -> {
            SentenceField newField = new SentenceField();
            newField.setPosition(generateFreePosition()); // z. B. nächste freie Position
            fieldModel.addField(newField);

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
     * Method is required to save the sentence type configuration.
     */
    public SentenceDefinition getDefinition() {
        SentenceDefinition def = new SentenceDefinition();

        SentenceMeta meta = new SentenceMeta();
        meta.setPaths(pathsModel.getPaths());
        meta.setPathPattern(pathPatternField.getText().trim());
        meta.setAppend(appendCheckbox.isSelected());

        def.setMeta(meta);
        def.setFields(fieldModel.getFields());

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
     * Sets the editor fields with the given sentence type data.
     * This is used to load existing sentence definitions for editing.
     *
     * @param name The name of the sentence type
     * @param def The SentenceDefinition containing the fields and metadata
     */
    public void setData(String name, SentenceDefinition def) {
        nameField.setText(name);
        originalKey = name;

        if (def.getMeta() != null) {
            pathPatternField.setText(def.getMeta().getPathPattern());
            appendCheckbox.setSelected(Boolean.TRUE.equals(def.getMeta().isAppend()));
            pathsModel.setPaths(def.getMeta().getPaths());
        }

        fieldModel.setFields(def.getFields());
    }

    private int generateFreePosition() {
        Set<Integer> used = fieldModel.getFields().keySet();
        int pos = 1;
        while (used.contains(pos)) {
            pos++;
        }
        return pos;
    }


}
