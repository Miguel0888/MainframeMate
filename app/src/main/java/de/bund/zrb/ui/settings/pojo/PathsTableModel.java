package de.bund.zrb.ui.settings.pojo;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

public class PathsTableModel extends AbstractTableModel {
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
