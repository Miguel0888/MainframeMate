package de.bund.zrb.mcpserver.research;

/**
 * Callback for archiving DOM snapshots captured after navigation.
 * <p>
 * Replaces the old {@link NetworkIngestionPipeline.IngestionCallback} for the
 * snapshot-based archiving approach. The callback is registered once by the
 * plugin layer (WebSearchPlugin) and invoked by the navigation tools after
 * each successful page load.
 */
public interface SnapshotArchivingCallback {

    /**
     * Called when a DOM snapshot has been captured.
     *
     * @param runId      the current research run ID
     * @param url        the page URL
     * @param mimeType   always "text/html" for snapshots
     * @param statusCode HTTP status (200 for snapshots)
     * @param bodyText   the full HTML content
     * @param capturedAt timestamp in ms
     * @return a document ID if successfully archived, or null
     */
    String onSnapshotCaptured(String runId, String url, String mimeType,
                              long statusCode, String bodyText, long capturedAt);
}

