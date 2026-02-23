package de.bund.zrb.indexing.port;

import de.bund.zrb.indexing.model.IndexSource;
import de.bund.zrb.indexing.model.ScannedItem;

import java.util.Iterator;
import java.util.List;

/**
 * Port interface for scanning a data source and discovering items to index.
 *
 * Each source type (LOCAL, FTP, MAIL, NDV, WEB) provides its own implementation.
 * The scanner only discovers items and their metadata – it does NOT extract content.
 *
 * Two scan modes:
 * - {@link #scan(IndexSource)} – batch: returns all items at once (simple, for small sources)
 * - {@link #scanStreaming(IndexSource)} – streaming: returns items one by one via Iterator
 *   (efficient for large sources like mail – processing starts immediately)
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
     * Streaming scan: returns items on demand via Iterator.
     * Processing can begin immediately without waiting for the full scan to finish.
     *
     * Default implementation wraps {@link #scan(IndexSource)}.
     * Override for large sources (e.g. MAIL) to provide true streaming.
     *
     * @param source the configured source
     * @return an iterator that yields items as they are discovered
     * @throws Exception if the source cannot be accessed
     */
    default Iterator<ScannedItem> scanStreaming(IndexSource source) throws Exception {
        return scan(source).iterator();
    }

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
