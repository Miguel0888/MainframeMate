package de.bund.zrb.ui.settings.pojo;

import de.zrb.bund.newApi.sentence.SentenceField;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.util.*;

public class FieldTableModel extends AbstractTableModel {
    private final String[] cols = {"Name", "Pos", "Länge", "Zeile", "Schema", "Farbe"};
    private final Map<Integer, SentenceField> fieldMap = new TreeMap<>();

    @Override
    public int getRowCount() {
        return fieldMap.size();
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
        SentenceField f = getFieldAt(row);
        if (f == null) return null;
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
        SentenceField f = getFieldAt(row);
        if (f == null) return;

        switch (col) {
            case 0: f.setName((String) value); break;
            case 1: f.setPosition(toInt(value)); break;
            case 2: f.setLength(toInt(value)); break;
            case 3: f.setRow(toInt(value)); break;
            case 4: f.setValuePattern((String) value); break;
            case 5: f.setColor((String) value); break;
        }
        updateKey(row, f); // handle repositioning in map
    }

    @Override
    public boolean isCellEditable(int row, int col) {
        return true;
    }

    @Override
    public Class<?> getColumnClass(int col) {
        return String.class;
    }

    public void addField(SentenceField field) {
        Integer pos = field.getPosition();
        if (pos == null) return;
        if (fieldMap.containsKey(pos)) return;
        fieldMap.put(pos, field);
        fireTableDataChanged();
    }

    public void removeField(int index) {
        Integer key = getKeyAt(index);
        if (key != null) {
            fieldMap.remove(key);
            fireTableDataChanged();
        }
    }

    public void setFields(Map<Integer, SentenceField> fields) {
        fieldMap.clear();
        if (fields != null) {
            fieldMap.putAll(fields);
        }
        fireTableDataChanged();
    }

    public Map<Integer, SentenceField> getFields() {
        return new TreeMap<>(fieldMap);
    }

    private Integer getKeyAt(int row) {
        return fieldMap.keySet().stream().skip(row).findFirst().orElse(null);
    }

    public SentenceField getFieldAt(int row) {
        return fieldMap.values().stream().skip(row).findFirst().orElse(null);
    }

    private void updateKey(int oldRowIndex, SentenceField updated) {
        // If position has changed, re-insert field at new key
        Integer oldKey = getKeyAt(oldRowIndex);
        Integer newKey = updated.getPosition();
        if (newKey == null || Objects.equals(oldKey, newKey)) return;

        fieldMap.remove(oldKey);
        if (fieldMap.containsKey(newKey)) {
            // Position conflict
            JOptionPane.showMessageDialog(null, "Position " + newKey + " ist bereits vergeben.", "Fehler", JOptionPane.ERROR_MESSAGE);
            updated.setPosition(oldKey); // zurücksetzen
        } else {
            fieldMap.put(newKey, updated);
        }
        fireTableDataChanged();
    }

    private Integer toInt(Object val) {
        try {
            return val == null ? null : Integer.parseInt(val.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}