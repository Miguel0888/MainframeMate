package de.bund.zrb.excel.service.cache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Store a snapshot of the first sheet's contents as plain strings.
 */
public final class ExcelSheetSnapshot {

    private final List<ExcelRowSnapshot> rows;

    public ExcelSheetSnapshot(List<ExcelRowSnapshot> rows) {
        this.rows = Collections.unmodifiableList(new ArrayList<>(rows));
    }

    public List<ExcelRowSnapshot> getRows() {
        return rows;
    }
}
