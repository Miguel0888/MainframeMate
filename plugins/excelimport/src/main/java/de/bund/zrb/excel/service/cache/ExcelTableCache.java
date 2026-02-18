package de.bund.zrb.excel.service.cache;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Cache parsed Excel tables.
 */
public interface ExcelTableCache {

    Optional<Map<String, List<String>>> get(ExcelTableKey key);

    void put(ExcelTableKey key, Map<String, List<String>> tableSnapshot);

    void invalidate(ExcelTableKey key);

    void clear();
}
