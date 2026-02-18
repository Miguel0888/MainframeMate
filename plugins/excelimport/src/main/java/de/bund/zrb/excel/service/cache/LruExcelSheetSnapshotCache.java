package de.bund.zrb.excel.service.cache;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Keep a small LRU cache for sheet snapshots.
 */
public class LruExcelSheetSnapshotCache implements ExcelSheetSnapshotCache {

    private final Map<ExcelSheetKey, ExcelSheetSnapshot> lru;

    public LruExcelSheetSnapshotCache(final int maxEntries) {
        this.lru = new LinkedHashMap<ExcelSheetKey, ExcelSheetSnapshot>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<ExcelSheetKey, ExcelSheetSnapshot> eldest) {
                return size() > maxEntries;
            }
        };
    }

    @Override
    public synchronized Optional<ExcelSheetSnapshot> get(ExcelSheetKey key) {
        return Optional.ofNullable(lru.get(key));
    }

    @Override
    public synchronized void put(ExcelSheetKey key, ExcelSheetSnapshot snapshot) {
        lru.put(key, snapshot);
    }

    @Override
    public synchronized void invalidate(ExcelSheetKey key) {
        lru.remove(key);
    }

    @Override
    public synchronized void clear() {
        lru.clear();
    }
}
