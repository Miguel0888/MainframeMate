package de.bund.zrb.excel.service.cache;

import java.util.Optional;

/**
 * Cache sheet snapshots to avoid reopening the same Excel file repeatedly.
 */
public interface ExcelSheetSnapshotCache {

    Optional<ExcelSheetSnapshot> get(ExcelSheetKey key);

    void put(ExcelSheetKey key, ExcelSheetSnapshot snapshot);

    void invalidate(ExcelSheetKey key);

    void clear();
}
