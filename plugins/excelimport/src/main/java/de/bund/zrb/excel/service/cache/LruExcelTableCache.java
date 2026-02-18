package de.bund.zrb.excel.service.cache;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Keep a small LRU cache to avoid re-parsing the same Excel file repeatedly.
 */
public class LruExcelTableCache implements ExcelTableCache {

    private final Map<ExcelTableKey, Map<String, List<String>>> lru;

    public LruExcelTableCache(final int maxEntries) {
        this.lru = new LinkedHashMap<ExcelTableKey, Map<String, List<String>>>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<ExcelTableKey, Map<String, List<String>>> eldest) {
                return size() > maxEntries;
            }
        };
    }

    @Override
    public synchronized Optional<Map<String, List<String>>> get(ExcelTableKey key) {
        return Optional.ofNullable(lru.get(key));
    }

    @Override
    public synchronized void put(ExcelTableKey key, Map<String, List<String>> tableSnapshot) {
        lru.put(key, tableSnapshot);
    }

    @Override
    public synchronized void invalidate(ExcelTableKey key) {
        lru.remove(key);
    }

    @Override
    public synchronized void clear() {
        lru.clear();
    }
}
