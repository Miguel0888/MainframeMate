package de.bund.zrb.ui.settings.pojo;

import de.zrb.bund.newApi.sentence.FieldCoordinate;
import de.zrb.bund.newApi.sentence.SentenceField;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.util.*;

public class FieldTableModel extends AbstractTableModel {
    private final String[] cols = {"Name", "Pos", "Länge", "Zeile", "Schema", "Farbe"};
    private final Map<FieldCoordinate, SentenceField> fieldMap = new TreeMap<>();

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
        Map.Entry<FieldCoordinate, SentenceField> entry = getEntryAt(row);
        if (entry == null) return null;
        FieldCoordinate coord = entry.getKey();
        SentenceField field = entry.getValue();

        switch (col) {
            case 0: return field.getName();
            case 1: return coord.getPosition();
            case 2: return field.getLength();
            case 3: return coord.getRow();
            case 4: return field.getValuePattern();
            case 5: return field.getColor();
            default: return null;
        }
    }

    @Override
    public void setValueAt(Object value, int row, int col) {
        Map.Entry<FieldCoordinate, SentenceField> entry = getEntryAt(row);
        if (entry == null) return;

        FieldCoordinate oldKey = entry.getKey();
        SentenceField field = entry.getValue();

        FieldCoordinate newKey = oldKey;

        switch (col) {
            case 0:
                field.setName((String) value);
                break;
            case 1:
                Integer newPos = toInt(value);
                if (newPos != null) newKey = new FieldCoordinate(oldKey.getRow(), newPos);
                break;
            case 2:
                field.setLength(toInt(value));
                break;
            case 3:
                Integer newRow = toInt(value);
                if (newRow != null) newKey = new FieldCoordinate(newRow, oldKey.getPosition());
                break;
            case 4:
                field.setValuePattern((String) value);
                break;
            case 5:
                field.setColor((String) value);
                break;
        }

        // Wenn sich der Key geändert hat, prüfen und neu einfügen
        if (!newKey.equals(oldKey)) {
            if (fieldMap.containsKey(newKey)) {
                JOptionPane.showMessageDialog(null, "Position/Zeile " + newKey + " ist bereits vergeben.",
                        "Fehler", JOptionPane.ERROR_MESSAGE);
            } else {
                fieldMap.remove(oldKey);
                fieldMap.put(newKey, field);
            }
        }

        fireTableDataChanged();
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
        // Default: suche freien Platz
        for (int row = 1; row <= 99; row++) {
            for (int pos = 1; pos <= 999; pos++) {
                FieldCoordinate key = new FieldCoordinate(row, pos);
                if (!fieldMap.containsKey(key)) {
                    fieldMap.put(key, field);
                    fireTableDataChanged();
                    return;
                }
            }
        }

        JOptionPane.showMessageDialog(null, "Kein freier Feldplatz gefunden", "Fehler", JOptionPane.ERROR_MESSAGE);
    }

    public void removeField(int index) {
        FieldCoordinate key = getKeyAt(index);
        if (key != null) {
            fieldMap.remove(key);
            fireTableDataChanged();
        }
    }

    public void setFields(Map<FieldCoordinate, SentenceField> fields) {
        fieldMap.clear();
        if (fields != null) {
            fieldMap.putAll(fields);
        }
        fireTableDataChanged();
    }

    private Map.Entry<FieldCoordinate, SentenceField> getEntryAt(int rowIndex) {
        return fieldMap.entrySet().stream().skip(rowIndex).findFirst().orElse(null);
    }

    private FieldCoordinate getKeyAt(int rowIndex) {
        return fieldMap.keySet().stream().skip(rowIndex).findFirst().orElse(null);
    }

    private Integer toInt(Object val) {
        try {
            return val == null ? null : Integer.parseInt(val.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public void addField(FieldCoordinate key, SentenceField field) {
        if (!fieldMap.containsKey(key)) {
            fieldMap.put(key, field);
            fireTableDataChanged();
        }
    }

    public SentenceField getFieldAt(int rowIndex) {
        return fieldMap.values().stream()
                .skip(rowIndex)
                .findFirst()
                .orElse(null);
    }

    public FieldCoordinate getCoordinateAt(int rowIndex) {
        return fieldMap.keySet().stream()
                .skip(rowIndex)
                .findFirst()
                .orElse(null);
    }

    public Map<FieldCoordinate, SentenceField> getInternalMap() {
        return fieldMap;
    }



}
