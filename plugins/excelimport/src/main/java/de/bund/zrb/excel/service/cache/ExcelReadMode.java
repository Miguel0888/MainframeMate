package de.bund.zrb.excel.service.cache;

/**
 * Define how to read an Excel file.
 */
public enum ExcelReadMode {

    /**
     * Use the in-memory cache when possible.
     */
    USE_CACHE,

    /**
     * Ignore the cache and read the file again.
     */
    FORCE_RELOAD
}
