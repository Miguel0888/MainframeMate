package de.bund.zrb.excel.service.cache;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Store cell values for a single row.
 */
public final class ExcelRowSnapshot {

    private final int rowIndex;
    private final Map<Integer, String> cellsByColumn;

    public ExcelRowSnapshot(int rowIndex, Map<Integer, String> cellsByColumn) {
        this.rowIndex = rowIndex;
        this.cellsByColumn = Collections.unmodifiableMap(new LinkedHashMap<>(cellsByColumn));
    }

    public int getRowIndex() {
        return rowIndex;
    }

    public Map<Integer, String> getCellsByColumn() {
        return cellsByColumn;
    }

    public String getCellValueOrEmpty(int columnIndex) {
        String value = cellsByColumn.get(columnIndex);
        return (value != null) ? value : "";
    }
}
