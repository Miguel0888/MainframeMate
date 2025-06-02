package de.bund.zrb.ui.settings;

import de.zrb.bund.newApi.sentence.SentenceDefinition;
import de.zrb.bund.newApi.sentence.SentenceField;
import de.zrb.bund.newApi.sentence.SentenceMeta;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class SentenceTypeEditor extends JPanel {

    private final JTextField nameField = new JTextField();
    private final JTextField pathPatternField = new JTextField();
    private final JCheckBox appendCheckbox = new JCheckBox("Anhängen erlaubt");

    private final FieldTableModel fieldModel = new FieldTableModel();
    private final JTable fieldTable = new JTable(fieldModel);

    public SentenceTypeEditor() {
        setLayout(new BorderLayout(8, 8));
        setBorder(new EmptyBorder(8, 8, 8, 8));

        pathPatternField.setToolTipText(
                "<html>Regulärer Ausdruck für den Datei-Pfad, z. B.:<br>" +
                        "<code>DATA\\.SA1\\d{2}</code><br>" +
                        "Hinweis: Doppelte Backslashes (\\) erforderlich für Punkte etc.</html>"
        );


        JPanel metaPanel = new JPanel(new GridLayout(3, 1, 4, 4));
        metaPanel.add(createLabeled("Satzart-Name", nameField));
        metaPanel.add(createLabeled("Pfad-Pattern (RegEx)", pathPatternField));
        metaPanel.add(appendCheckbox);

        JButton addFieldButton = new JButton("➕ Feld");
        addFieldButton.addActionListener(e -> {
            fieldModel.fields.add(new SentenceField());
            fieldModel.fireTableDataChanged();
        });

        JButton removeFieldButton = new JButton("🗑️ Entfernen");
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

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.add(addFieldButton);
        buttonPanel.add(removeFieldButton);
        buttonPanel.add(colorButton);

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

        add(metaPanel, BorderLayout.NORTH);
        add(new JScrollPane(fieldTable), BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private JPanel createLabeled(String label, JComponent component) {
        JPanel panel = new JPanel(new BorderLayout(4, 0));
        panel.add(new JLabel(label), BorderLayout.WEST);
        panel.add(component, BorderLayout.CENTER);
        return panel;
    }

    public SentenceDefinition getDefinition() {
        SentenceDefinition def = new SentenceDefinition();

        SentenceMeta meta = new SentenceMeta();
        meta.setPathPattern(pathPatternField.getText().trim());
        meta.setAppend(appendCheckbox.isSelected());

        def.setMeta(meta);
        def.setFields(new ArrayList<>(fieldModel.fields));

        for (SentenceField f : def.getFields()) {
            String regex = f.getValuePattern();
            if (regex != null && !regex.trim().isEmpty()) {
                try {
                    java.util.regex.Pattern.compile(regex);
                } catch (java.util.regex.PatternSyntaxException e) {
                    JOptionPane.showMessageDialog(this,
                            "Ungültiger regulärer Ausdruck in Feld '" + f.getName() + "':\n" + regex,
                            "Regex-Fehler", JOptionPane.ERROR_MESSAGE);
                    return null;
                }
            }
        }

        return def;
    }

    public String getKey() {
        return nameField.getText().trim();
    }

    public void setData(String name, SentenceDefinition def) {
        nameField.setText(name);

        if (def.getMeta() != null) {
            pathPatternField.setText(def.getMeta().getPathPattern());
            appendCheckbox.setSelected(Boolean.TRUE.equals(def.getMeta().isAppend()));
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
}
