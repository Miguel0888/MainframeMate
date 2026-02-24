package de.bund.zrb.ui.settings.categories;

import de.bund.zrb.model.Settings;
import de.bund.zrb.ui.help.HelpContentProvider;
import de.bund.zrb.ui.settings.FormBuilder;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Map;

public class ColorSettingsPanel extends AbstractSettingsPanel {

    private final ColorOverrideTableModel colorModel;
    private final JTable colorTable;

    public ColorSettingsPanel(Component parent) {
        super("colors", "Farbzuordnung");
        FormBuilder fb = new FormBuilder();

        colorModel = new ColorOverrideTableModel(settings.fieldColorOverrides);
        colorTable = new JTable(colorModel);
        colorTable.getColumnModel().getColumn(1).setCellEditor(new ColorCellEditor());
        colorTable.getColumnModel().getColumn(1).setCellRenderer((table, value, isSelected, hasFocus, row, col) -> {
            JLabel label = new JLabel(value != null ? value.toString() : "");
            label.setOpaque(true);
            label.setBackground(parseHexColor(String.valueOf(value), Color.WHITE));
            return label;
        });
        colorTable.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2) {
                    int row = colorTable.rowAtPoint(e.getPoint());
                    int column = colorTable.columnAtPoint(e.getPoint());
                    if (column == 1) colorTable.editCellAt(row, column);
                }
            }
        });
        colorTable.setFillsViewportHeight(true);
        colorTable.setPreferredScrollableViewportSize(new Dimension(300, 150));
        fb.addWideGrow(new JScrollPane(colorTable));

        JButton addRowButton = new JButton("➕ Hinzufügen");
        addRowButton.addActionListener(e -> {
            String key = JOptionPane.showInputDialog(parent, "Feldname eingeben:");
            if (key != null && !key.trim().isEmpty()) colorModel.addEntry(key.trim().toUpperCase(), "#00AA00");
        });
        JButton removeRowButton = new JButton("➖ Entfernen");
        removeRowButton.addActionListener(e -> { int s = colorTable.getSelectedRow(); if (s >= 0) colorModel.removeEntry(s); });
        fb.addButtons(addRowButton, removeRowButton);

        installPanel(fb);
    }

    @Override
    protected void applyToSettings(Settings s) {
        s.fieldColorOverrides.clear();
        s.fieldColorOverrides.putAll(colorModel.getColorMap());
    }

    private static Color parseHexColor(String hex, Color fallback) {
        if (hex == null) return fallback;
        try { return Color.decode(hex); } catch (NumberFormatException ex) { return fallback; }
    }

    // ────────── inner classes ──────────

    private static class ColorOverrideTableModel extends AbstractTableModel {
        private final java.util.List<String> keys;
        private final Map<String, String> colorMap;

        public ColorOverrideTableModel(Map<String, String> colorMap) {
            this.colorMap = colorMap;
            this.keys = new ArrayList<>(colorMap.keySet());
        }

        Map<String, String> getColorMap() { return colorMap; }

        @Override public int getRowCount() { return keys.size(); }
        @Override public int getColumnCount() { return 2; }
        @Override public String getColumnName(int col) { return col == 0 ? "Feldname" : "Farbe (Hex)"; }
        @Override public Object getValueAt(int row, int col) { String key = keys.get(row); return col == 0 ? key : colorMap.get(key); }
        @Override public boolean isCellEditable(int row, int col) { return col == 1; }

        @Override public void setValueAt(Object value, int row, int col) {
            if (col == 1) colorMap.put(keys.get(row), value.toString());
        }

        public void addEntry(String name, String color) { colorMap.put(name, color); keys.add(name); fireTableDataChanged(); }
        public void removeEntry(int rowIndex) {
            if (rowIndex >= 0 && rowIndex < keys.size()) { colorMap.remove(keys.get(rowIndex)); keys.remove(rowIndex); fireTableDataChanged(); }
        }
    }

    private static class ColorCellEditor extends AbstractCellEditor implements TableCellEditor {
        private final JButton button = new JButton();
        private String currentColorHex;

        public ColorCellEditor() {
            button.addActionListener(e -> {
                Color initialColor = parseHexColor(currentColorHex, Color.BLACK);
                Color selectedColor = JColorChooser.showDialog(button, "Farbe wählen", initialColor);
                if (selectedColor != null) {
                    currentColorHex = "#" + Integer.toHexString(selectedColor.getRGB()).substring(2).toUpperCase();
                    button.setBackground(selectedColor);
                    fireEditingStopped();
                }
            });
        }

        @Override public Object getCellEditorValue() { return currentColorHex; }
        @Override public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            currentColorHex = String.valueOf(value);
            button.setBackground(parseHexColor(currentColorHex, Color.BLACK));
            return button;
        }
    }
}

