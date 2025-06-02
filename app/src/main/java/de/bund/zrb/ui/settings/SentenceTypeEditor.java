package de.bund.zrb.ui.settings;

import de.zrb.bund.newApi.sentence.SentenceDefinition;
import de.zrb.bund.newApi.sentence.SentenceField;
import de.zrb.bund.newApi.sentence.SentenceMeta;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

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
            fieldModel.fields.add(new SentenceField());
            fieldModel.fireTableDataChanged();
        });

        JButton removeFieldButton = new JButton("❌ Entfernen");
        removeFieldButton.addActionListener(e -> {
            int selected = fieldTable.getSelectedRow();
            if (selected >= 0) {
                fieldModel.fields.remove(selected);
                fieldModel.fireTableDataChanged();
            }
        });

        JButton colorButton = new JButton("🎨 Farbe wählen");
        colorButton.addActionListener(e -> {
            int selected = fieldTable.getSelectedRow();
            if (selected >= 0) {
                SentenceField f = fieldModel.fields.get(selected);
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
        def.setFields(new ArrayList<>(fieldModel.fields));

        if (!sanitizeAndCheck(def)) return null;

        return def;
    }

    private boolean sanitizeAndCheck(SentenceDefinition def) {
        for (SentenceField f : def.getFields()) {
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
        // merken!
        originalKey = name;

        if (def.getMeta() != null) {
            pathPatternField.setText(def.getMeta().getPathPattern());
            appendCheckbox.setSelected(Boolean.TRUE.equals(def.getMeta().isAppend()));
            pathsModel.setPaths(def.getMeta().getPaths());
        }

        fieldModel.fields = new ArrayList<>(def.getFields());
        fieldModel.fireTableDataChanged();
    }

    private static class FieldTableModel extends AbstractTableModel {
        private final String[] cols = {"Name", "Pos", "Länge", "Zeile", "Schema", "Farbe"};
        private List<SentenceField> fields = new ArrayList<>();

        @Override
        public int getRowCount() {
            return fields.size();
        }

        @Override
        public int getColumnCount() {
            return cols.length;
        }

        @Override
        public String getColumnName(int col) {
            return cols[col];
        }

        @Override
        public Object getValueAt(int row, int col) {
            SentenceField f = fields.get(row);
            switch (col) {
                case 0: return f.getName();
                case 1: return f.getPosition();
                case 2: return f.getLength();
                case 3: return f.getRow();
                case 4: return f.getValuePattern();
                case 5: return f.getColor();
                default: return null;
            }
        }

        @Override
        public void setValueAt(Object value, int row, int col) {
            SentenceField f = fields.get(row);
            switch (col) {
                case 0: f.setName((String) value); break;
                case 1: f.setPosition(toInt(value)); break;
                case 2: f.setLength(toInt(value)); break;
                case 3: f.setRow(toInt(value)); break;
                case 4: f.setValuePattern((String) value); break;
                case 5: f.setColor((String) value); break;
            }
        }

        @Override
        public boolean isCellEditable(int row, int col) {
            return true;
        }

        @Override
        public Class<?> getColumnClass(int col) {
            return String.class;
        }

        private Integer toInt(Object val) {
            try {
                return val == null ? null : Integer.parseInt(val.toString());
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    private static class PathsTableModel extends AbstractTableModel {
        private final List<String> paths = new ArrayList<>();

        @Override
        public int getRowCount() {
            return paths.size();
        }

        @Override
        public int getColumnCount() {
            return 1;
        }

        @Override
        public String getColumnName(int column) {
            return "Pfad";
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            return paths.get(rowIndex);
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            paths.set(rowIndex, aValue.toString());
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return true;
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return String.class;
        }

        public void setPaths(List<String> newPaths) {
            paths.clear();
            if (newPaths != null) {
                paths.addAll(newPaths);
            }
            fireTableDataChanged();
        }

        public List<String> getPaths() {
            return new ArrayList<>(paths);
        }

        public void addPath(String path) {
            paths.add(path);
            fireTableRowsInserted(paths.size() - 1, paths.size() - 1);
        }

        public void removePath(int index) {
            paths.remove(index);
            fireTableRowsDeleted(index, index);
        }
    }

}
