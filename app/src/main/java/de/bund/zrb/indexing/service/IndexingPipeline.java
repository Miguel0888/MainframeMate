package de.bund.zrb.indexing.service;

import de.bund.zrb.indexing.model.*;
import de.bund.zrb.indexing.port.SourceScanner;
import de.bund.zrb.indexing.store.IndexSourceRepository;
import de.bund.zrb.indexing.store.IndexStatusStore;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Orchestrates the indexing pipeline for a single source:
 *
 *   1. Scan (discover items via SourceScanner)
 *   2. Delta detection (compare with IndexItemStatus)
 *   3. Process changed items (Fetch → Extract → Chunk → Embed → Index)
 *   4. Handle deletions (tombstone)
 *   5. Persist status
 *
 * The pipeline is source-agnostic – source-specific logic lives in SourceScanner.
 * Text extraction, chunking, and indexing reuse the existing RAG infrastructure.
 *
 * Usage:
 *   IndexingPipeline pipeline = new IndexingPipeline(statusStore, scannerRegistry);
 *   IndexRunStatus result = pipeline.runForSource(source);
 */
public class IndexingPipeline {

    private static final Logger LOG = Logger.getLogger(IndexingPipeline.class.getName());
    private static final int INDEX_SCHEMA_VERSION = 1;

    private final IndexStatusStore statusStore;
    private final Map<SourceType, SourceScanner> scanners = new HashMap<>();

    // Callback interface for the processing step (injected by caller to use RAG infra)
    private ContentProcessor contentProcessor;

    /**
     * Callback for processing a single item's content.
     * Implementations should: extract text (Tika), chunk, embed, write to Lucene/Vector.
     */
    public interface ContentProcessor {
        /**
         * Process an item's raw content and write to indices.
         *
         * @param source the source configuration
         * @param itemPath the item path
         * @param content raw content bytes
         * @param mimeType detected MIME type (may be null)
         * @return number of chunks created
         * @throws Exception if processing fails
         */
        int process(IndexSource source, String itemPath, byte[] content, String mimeType) throws Exception;

        /**
         * Remove an item from all indices.
         */
        void removeFromIndex(String documentId) throws Exception;
    }

    public IndexingPipeline(IndexStatusStore statusStore) {
        this.statusStore = statusStore;
    }

    public void registerScanner(SourceType type, SourceScanner scanner) {
        scanners.put(type, scanner);
    }

    public void setContentProcessor(ContentProcessor processor) {
        this.contentProcessor = processor;
    }

    /**
     * Run the full indexing pipeline for a source.
     *
     * @param source the configured source
     * @return run status with counters
     */
    public IndexRunStatus runForSource(IndexSource source) {
        IndexRunStatus run = new IndexRunStatus();
        run.setSourceId(source.getSourceId());
        run.setStartedAt(System.currentTimeMillis());

        String sourceId = source.getSourceId();
        LOG.info("[Indexing] Starting run for: " + source.getName() + " (" + source.getSourceType() + ")");

        try {
            // ── 1. Get scanner ──
            SourceScanner scanner = scanners.get(source.getSourceType());
            if (scanner == null) {
                throw new IllegalStateException("No scanner registered for: " + source.getSourceType());
            }

            // ── Max duration enforcement ──
            long maxDurationMs = source.getMaxDurationMinutes() > 0
                    ? source.getMaxDurationMinutes() * 60_000L : Long.MAX_VALUE;
            long deadline = run.getStartedAt() + maxDurationMs;

            // ── 2. Load existing statuses for delta detection ──
            Map<String, IndexItemStatus> existingStatuses = statusStore.loadItemStatuses(sourceId);
            Set<String> seenPaths = new HashSet<>();
            boolean timedOut = false;

            // ── 3. Stream items: scan + process on-demand ──
            // Items are processed as they arrive – no need to collect all first.
            Iterator<ScannedItem> itemIterator = scanner.scanStreaming(source);
            int scannedCount = 0;

            while (itemIterator.hasNext()) {
                ScannedItem item = itemIterator.next();
                scannedCount++;
                seenPaths.add(item.getPath());

                // Check timeout
                if (System.currentTimeMillis() >= deadline) {
                    if (!timedOut) {
                        LOG.info("[Indexing] Max duration reached (" + source.getMaxDurationMinutes()
                                + " min). Stopping processing. Remaining items will be picked up next run.");
                        timedOut = true;
                    }
                    // After timeout: still consume iterator to track seen paths (for deletion detection)
                    // but don't process. Break after a reasonable additional count to avoid infinite iteration.
                    // For streaming scanners, we can't know the total – just stop.
                    break;
                }

                // Delta detection
                IndexItemStatus existing = existingStatuses.get(item.getPath());

                if (existing == null) {
                    run.incNew();
                    processItem(source, scanner, item, existingStatuses, sourceId);
                } else if (existing.needsReindex(item.getLastModified(), item.getSize())) {
                    run.incChanged();
                    processItem(source, scanner, item, existingStatuses, sourceId);
                } else {
                    run.incUnchanged();
                }

                // Log progress periodically
                if (scannedCount % 500 == 0) {
                    LOG.info("[Indexing] Progress: " + scannedCount + " items processed"
                            + " (new=" + run.getItemsNew() + " changed=" + run.getItemsChanged()
                            + " unchanged=" + run.getItemsUnchanged() + ")");
                }
            }

            run.setItemsScanned(scannedCount);

            // If timed out, do NOT delete anything – we didn't see all items
            if (!timedOut) {
                // ── 4. Handle deletions ──
                for (Map.Entry<String, IndexItemStatus> entry : existingStatuses.entrySet()) {
                    if (!seenPaths.contains(entry.getKey())
                            && entry.getValue().getState() != IndexItemState.DELETED) {
                        IndexItemStatus status = entry.getValue();
                        status.setState(IndexItemState.DELETED);
                        status.setDeletedAt(System.currentTimeMillis());

                        if (contentProcessor != null) {
                            try {
                                contentProcessor.removeFromIndex(entry.getKey());
                            } catch (Exception e) {
                                LOG.log(Level.WARNING, "[Indexing] Error removing deleted item: " + entry.getKey(), e);
                            }
                        }
                        run.incDeleted();
                    }
                }
            }

            // ── 5. Persist all statuses ──
            statusStore.saveItemStatuses(sourceId, existingStatuses);

            run.setRunState(IndexRunStatus.RunState.COMPLETED);

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "[Indexing] Run failed for: " + source.getName(), e);
            run.setRunState(IndexRunStatus.RunState.FAILED);
            run.setLastError(e.getMessage());
        }

        run.setCompletedAt(System.currentTimeMillis());
        statusStore.saveRun(run);

        LOG.info("[Indexing] Run completed: " + run);
        return run;
    }

    // ─── Process a single item ───

    private void processItem(IndexSource source, SourceScanner scanner, ScannedItem item,
                              Map<String, IndexItemStatus> statuses, String sourceId) {
        IndexItemStatus status = statuses.get(item.getPath());
        if (status == null) {
            status = new IndexItemStatus();
            status.setSourceId(sourceId);
            status.setItemPath(item.getPath());
        }

        status.setLastModifiedAt(item.getLastModified());
        status.setFileSize(item.getSize());

        try {
            if (contentProcessor == null) {
                // No processor registered – just track the status
                status.setState(IndexItemState.PENDING);
                status.setSkipReason("No ContentProcessor registered");
            } else {
                // Fetch content
                byte[] content = scanner.fetchContent(source, item.getPath());

                // Process (extract → chunk → embed → index)
                int chunkCount = contentProcessor.process(source, item.getPath(), content, item.getMimeType());

                status.setState(IndexItemState.INDEXED);
                status.setIndexedAt(System.currentTimeMillis());
                status.setChunkCount(chunkCount);
                status.setIndexSchemaVersion(INDEX_SCHEMA_VERSION);
                status.setErrorMessage(null);
                status.setErrorCount(0);
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[Indexing] Error processing: " + item.getPath(), e);
            status.setState(IndexItemState.ERROR);
            status.setErrorMessage(e.getMessage());
            status.setErrorCount(status.getErrorCount() + 1);
        }

        statuses.put(item.getPath(), status);
    }
}
