package de.bund.zrb.excel.service.cache;

import java.lang.ref.SoftReference;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unbounded cache with soft-referenced values to allow GC-based cleanup under memory pressure.
 */
public class SoftReferenceExcelSheetSnapshotCache implements ExcelSheetSnapshotCache {

    private final ConcurrentHashMap<ExcelSheetKey, SoftReference<ExcelSheetSnapshot>> cache = new ConcurrentHashMap<>();

    @Override
    public Optional<ExcelSheetSnapshot> get(ExcelSheetKey key) {
        SoftReference<ExcelSheetSnapshot> reference = cache.get(key);
        if (reference == null) {
            return Optional.empty();
        }

        ExcelSheetSnapshot value = reference.get();
        if (value == null) {
            cache.remove(key, reference);
            return Optional.empty();
        }

        return Optional.of(value);
    }

    @Override
    public void put(ExcelSheetKey key, ExcelSheetSnapshot snapshot) {
        cache.put(key, new SoftReference<>(snapshot));
    }

    @Override
    public void invalidate(ExcelSheetKey key) {
        cache.remove(key);
    }

    @Override
    public void clear() {
        cache.clear();
    }
}
