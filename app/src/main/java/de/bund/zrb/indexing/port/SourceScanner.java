package de.bund.zrb.indexing.port;

import de.bund.zrb.indexing.model.IndexSource;
import de.bund.zrb.indexing.model.ScannedItem;

import java.util.List;

/**
 * Port interface for scanning a data source and discovering items to index.
 *
 * Each source type (LOCAL, FTP, MAIL, NDV, WEB) provides its own implementation.
 * The scanner only discovers items and their metadata – it does NOT extract content.
 */
public interface SourceScanner {

    /**
     * Scan the source and return all items within the configured scope.
     * The caller compares results with IndexItemStatus to determine the delta.
     *
     * @param source the configured source with scope/filter settings
     * @return list of discovered items with metadata for change detection
     * @throws Exception if the source cannot be accessed
     */
    List<ScannedItem> scan(IndexSource source) throws Exception;

    /**
     * Read the raw content bytes of a single item.
     * Called during the processing phase after delta detection.
     *
     * @param source the source configuration
     * @param itemPath the path as returned by scan()
     * @return raw content bytes
     * @throws Exception if the item cannot be read
     */
    byte[] fetchContent(IndexSource source, String itemPath) throws Exception;
}
