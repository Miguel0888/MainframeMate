package de.bund.zrb.mcpserver.research;

import de.bund.zrb.WebDriver;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Network-First Ingestion Pipeline for the Research Tool Suite.
 * <p>
 * CURRENTLY DISABLED: All network intercept / data collector logic has been
 * removed to prevent browser freezes. The pipeline shell remains so that
 * callers (ResearchNavigateTool, ResearchOpenTool, etc.) keep compiling.
 * The start()/stop() methods are no-ops.
 */
public class NetworkIngestionPipeline {

    private static final Logger LOG = Logger.getLogger(NetworkIngestionPipeline.class.getName());

    private final WebDriver driver;
    private final ResearchSession session;
    private final AtomicBoolean active = new AtomicBoolean(false);
    private final AtomicInteger capturedCount = new AtomicInteger(0);
    private final AtomicInteger skippedCount = new AtomicInteger(0);
    private final AtomicInteger failedCount = new AtomicInteger(0);

    // ── HTML body cache for Jsoup-based link extraction ──
    private volatile String lastNavigationHtml;
    private volatile String lastNavigationUrl;

    // ── DevTools-style resource category counters ──
    private final ConcurrentHashMap<ResourceCategory, AtomicInteger> categoryCounts = new ConcurrentHashMap<>();

    // Callback for storing captured data (kept for API compatibility)
    private IngestionCallback callback;

    /**
     * Callback interface for the captured network data.
     * Implementations handle H2 storage and Lucene indexing.
     */
    public interface IngestionCallback {
        /**
         * Called when a response body has been successfully captured.
         *
         * @param runId        the research run ID (Data Lake)
         * @param url          the response URL
         * @param mimeType     the response MIME type
         * @param status       HTTP status code
         * @param bodyText     the decoded body text (may be truncated)
         * @param headers      filtered headers (allowlist only)
         * @param capturedAt   timestamp of capture
         * @return docId of the stored document, or null on failure
         */
        String onBodyCaptured(String runId, String url, String mimeType, long status,
                              String bodyText, Map<String, String> headers, long capturedAt);
    }

    /**
     * Global default callback that plugins can register at startup.
     * If set, all new pipelines will use this callback (unless explicitly overridden).
     */
    private static volatile IngestionCallback globalDefaultCallback;

    /**
     * Register a global default callback for all new NetworkIngestionPipelines.
     * Called once at plugin init time (e.g. from WebSearchPlugin).
     */
    public static void setGlobalDefaultCallback(IngestionCallback callback) {
        globalDefaultCallback = callback;
        LOG.info("[NetworkIngestion] Global default callback registered");
    }

    /**
     * Get the global default callback, or null if none registered.
     */
    public static IngestionCallback getGlobalDefaultCallback() {
        return globalDefaultCallback;
    }

    public NetworkIngestionPipeline(WebDriver driver, ResearchSession session) {
        this.driver = driver;
        this.session = session;
    }

    /**
     * Start the pipeline: DISABLED – network intercept ausgebaut.
     * Die Methode setzt nur das active-Flag, registriert aber keine Network-Listener.
     */
    public synchronized void start(IngestionCallback callback) {
        if (active.get()) {
            LOG.fine("[NetworkIngestion] Already active for session " + session.getSessionId());
            return;
        }
        this.callback = callback;
        active.set(true);
        LOG.info("[NetworkIngestion] Pipeline start SKIPPED (network disabled) for session " + session.getSessionId());
    }

    /**
     * Stop the pipeline: DISABLED – network intercept ausgebaut.
     */
    public synchronized void stop() {
        if (!active.get()) return;
        active.set(false);
        LOG.info("[NetworkIngestion] Pipeline stopped (network disabled). Captured=" + capturedCount.get()
                + " Skipped=" + skippedCount.get() + " Failed=" + failedCount.get());
    }

    // ── Status ──────────────────────────────────────────────────────

    public boolean isActive() { return active.get(); }
    public int getCapturedCount() { return capturedCount.get(); }
    public int getSkippedCount() { return skippedCount.get(); }
    public int getFailedCount() { return failedCount.get(); }

    // ── HTML body cache (for Jsoup link extraction) ─────────────────

    /** Last captured navigation HTML body (text/html, status 2xx). */
    public String getLastNavigationHtml() { return lastNavigationHtml; }

    /** URL of the last captured navigation HTML. */
    public String getLastNavigationUrl() { return lastNavigationUrl; }

    /** Pre-set the navigation URL (before actual navigation) for same-URL detection after timeouts. */
    public void setLastNavigationUrl(String url) { this.lastNavigationUrl = url; }

    /** Clear the cached HTML (e.g. before a new navigation). */
    public void clearNavigationCache() {
        lastNavigationHtml = null;
        lastNavigationUrl = null;
    }

    // ── Resource category counters ──────────────────────────────────

    /** Increment counter for a resource category. */
    void countCategory(ResourceCategory cat) {
        categoryCounts.computeIfAbsent(cat, k -> new AtomicInteger(0)).incrementAndGet();
    }

    /** Get count for a specific category. */
    public int getCategoryCount(ResourceCategory cat) {
        AtomicInteger c = categoryCounts.get(cat);
        return c != null ? c.get() : 0;
    }

    /** Get all category counts as a map. */
    public Map<String, Integer> getCategoryCounts() {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (ResourceCategory cat : ResourceCategory.values()) {
            int c = getCategoryCount(cat);
            if (c > 0) result.put(cat.name(), c);
        }
        return result;
    }
}
