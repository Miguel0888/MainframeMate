package de.bund.zrb.excel.service.cache;

import java.lang.ref.SoftReference;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unbounded cache with soft-referenced values to allow GC-based cleanup under memory pressure.
 */
public class SoftReferenceExcelTableCache implements ExcelTableCache {

    private final ConcurrentHashMap<ExcelTableKey, SoftReference<Map<String, List<String>>>> cache = new ConcurrentHashMap<>();

    @Override
    public Optional<Map<String, List<String>>> get(ExcelTableKey key) {
        SoftReference<Map<String, List<String>>> reference = cache.get(key);
        if (reference == null) {
            return Optional.empty();
        }

        Map<String, List<String>> value = reference.get();
        if (value == null) {
            cache.remove(key, reference);
            return Optional.empty();
        }

        return Optional.of(value);
    }

    @Override
    public void put(ExcelTableKey key, Map<String, List<String>> tableSnapshot) {
        cache.put(key, new SoftReference<>(tableSnapshot));
    }

    @Override
    public void invalidate(ExcelTableKey key) {
        cache.remove(key);
    }

    @Override
    public void clear() {
        cache.clear();
    }
}
