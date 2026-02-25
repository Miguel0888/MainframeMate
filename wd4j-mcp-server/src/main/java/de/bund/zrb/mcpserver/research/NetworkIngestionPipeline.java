package de.bund.zrb.mcpserver.research;

import de.bund.zrb.WebDriver;
import de.bund.zrb.event.WDNetworkEvent;
import de.bund.zrb.type.network.*;
import de.bund.zrb.type.session.WDSubscriptionRequest;
import de.bund.zrb.websocket.WDEventNames;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Network-First Ingestion Pipeline for the Research Tool Suite.
 * <p>
 * Implements the "Network Plane" from the architecture specification:
 * <ol>
 *   <li>{@code network.addDataCollector} activated per session</li>
 *   <li>On {@code network.responseCompleted}: decides by URL/MimeType/Status whether to fetch body</li>
 *   <li>Body fetched with {@code network.getData}, then disowned with {@code network.disownData}</li>
 *   <li>Content stored via callback (H2 archive) and indexed (Lucene)</li>
 * </ol>
 * <p>
 * Asynchronicity requirement: {@code getData} may not immediately return the body
 * after {@code responseCompleted}. Implements retry/backoff (3 attempts, 100-300ms jitter).
 * <p>
 * Privacy/Security:
 * <ul>
 *   <li>Bodies only for allowlisted MIME types and max size</li>
 *   <li>Login endpoints and auth URLs excluded</li>
 *   <li>Headers filtered by session's headerAllowlist</li>
 * </ul>
 */
public class NetworkIngestionPipeline {

    private static final Logger LOG = Logger.getLogger(NetworkIngestionPipeline.class.getName());

    // Default MIME types to capture
    private static final Set<String> DEFAULT_CAPTURE_MIMES = new LinkedHashSet<>(Arrays.asList(
            "text/html", "text/plain", "text/xml", "text/csv",
            "application/json", "application/xml", "application/xhtml+xml",
            "application/atom+xml", "application/rss+xml",
            "application/ld+json", "application/feed+json"
    ));

    // URL patterns to never capture (auth/login endpoints)
    private static final List<String> EXCLUDED_URL_PATTERNS = Arrays.asList(
            "/login", "/signin", "/sign-in", "/auth", "/oauth",
            "/token", "/logout", "/signout", "/sign-out",
            "/password", "/register", "/signup", "/sign-up",
            "/checkout", "/payment", "/pay/"
    );

    // Retry settings for getData
    private static final int GET_DATA_MAX_RETRIES = 3;
    private static final long GET_DATA_RETRY_BASE_MS = 100;
    private static final long GET_DATA_RETRY_MAX_MS = 300;

    private final WebDriver driver;
    private final ResearchSession session;
    private WDCollector collector;
    private final AtomicBoolean active = new AtomicBoolean(false);
    private final AtomicInteger capturedCount = new AtomicInteger(0);
    private final AtomicInteger skippedCount = new AtomicInteger(0);
    private final AtomicInteger failedCount = new AtomicInteger(0);

    // Async ingestion
    private final ExecutorService ingestionExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "NetworkIngestion-" + session.getSessionId());
            t.setDaemon(true);
            return t;
        }
    });

    // Callback for storing captured data
    private IngestionCallback callback;

    // Event listener reference (for cleanup)
    private Consumer<WDNetworkEvent.ResponseCompleted> responseListener;

    /**
     * Callback interface for the captured network data.
     * Implementations handle H2 storage and Lucene indexing.
     */
    public interface IngestionCallback {
        /**
         * Called when a response body has been successfully captured.
         *
         * @param url          the response URL
         * @param mimeType     the response MIME type
         * @param status       HTTP status code
         * @param bodyText     the decoded body text (may be truncated)
         * @param headers      filtered headers (allowlist only)
         * @param capturedAt   timestamp of capture
         * @return docId of the stored document, or null on failure
         */
        String onBodyCaptured(String url, String mimeType, long status,
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
     * Start the pipeline: register data collector and subscribe to responseCompleted events.
     */
    public synchronized void start(IngestionCallback callback) {
        if (active.get()) {
            LOG.fine("[NetworkIngestion] Already active for session " + session.getSessionId());
            return;
        }

        this.callback = callback;

        try {
            // 1. Register a response body collector (max 2MB default)
            int maxSize = session.getMaxBytesPerDoc();
            collector = driver.network().addResponseBodyCollector(maxSize);
            LOG.info("[NetworkIngestion] DataCollector registered: " + collector.value()
                    + " (maxSize=" + maxSize + ")");

            // 2. Subscribe to network.responseCompleted events
            WDSubscriptionRequest subReq = new WDSubscriptionRequest(
                    Collections.singletonList(WDEventNames.RESPONSE_COMPLETED.getName()));

            responseListener = new Consumer<WDNetworkEvent.ResponseCompleted>() {
                @Override
                public void accept(WDNetworkEvent.ResponseCompleted event) {
                    handleResponseCompleted(event);
                }
            };

            driver.addEventListener(subReq, responseListener);
            active.set(true);
            LOG.info("[NetworkIngestion] Pipeline started for session " + session.getSessionId());

        } catch (Exception e) {
            LOG.log(Level.WARNING, "[NetworkIngestion] Failed to start pipeline", e);
        }
    }

    /**
     * Stop the pipeline: remove collector and unsubscribe.
     */
    public synchronized void stop() {
        if (!active.get()) return;
        active.set(false);

        try {
            if (responseListener != null) {
                driver.removeEventListener(
                        WDEventNames.RESPONSE_COMPLETED.getName(), responseListener);
                responseListener = null;
            }
        } catch (Exception e) {
            LOG.fine("[NetworkIngestion] Error removing event listener: " + e.getMessage());
        }

        try {
            if (collector != null) {
                driver.network().removeDataCollector(collector);
                collector = null;
            }
        } catch (Exception e) {
            LOG.fine("[NetworkIngestion] Error removing collector: " + e.getMessage());
        }

        ingestionExecutor.shutdown();
        LOG.info("[NetworkIngestion] Pipeline stopped. Captured=" + capturedCount.get()
                + " Skipped=" + skippedCount.get() + " Failed=" + failedCount.get());
    }

    /**
     * Handle a network.responseCompleted event.
     * Decides whether to fetch the body based on URL, MIME type, status, and policies.
     */
    private void handleResponseCompleted(WDNetworkEvent.ResponseCompleted event) {
        if (!active.get() || event == null) return;

        try {
            WDNetworkEvent.ResponseCompleted.ResponseCompletedParametersWD params = event.getParams();
            if (params == null || params.getResponse() == null || params.getRequest() == null) return;

            WDResponseData response = params.getResponse();
            WDRequestData requestData = params.getRequest();
            String url = response.getUrl();
            String mimeType = response.getMimeType();
            long status = response.getStatus();
            WDRequest requestRef = requestData.getRequest();

            // Filter: status must be 2xx
            if (status < 200 || status >= 300) {
                skippedCount.incrementAndGet();
                return;
            }

            // Filter: MIME type must be capturable
            if (!isCaptureableMime(mimeType)) {
                skippedCount.incrementAndGet();
                return;
            }

            // Filter: excluded URL patterns (auth/login)
            if (isExcludedUrl(url)) {
                skippedCount.incrementAndGet();
                return;
            }

            // Filter: domain policy
            if (!session.isUrlAllowed(url)) {
                skippedCount.incrementAndGet();
                return;
            }

            // Filter: body size limit
            if (response.getBodySize() > session.getMaxBytesPerDoc()) {
                skippedCount.incrementAndGet();
                LOG.fine("[NetworkIngestion] Skipping oversized response: " + url
                        + " (" + response.getBodySize() + " bytes)");
                return;
            }

            // Async: fetch body and store
            ingestionExecutor.submit(new Runnable() {
                @Override
                public void run() {
                    fetchAndStore(url, mimeType, status, requestRef, response);
                }
            });

        } catch (Exception e) {
            LOG.log(Level.FINE, "[NetworkIngestion] Error handling responseCompleted", e);
        }
    }

    /**
     * Fetch the response body with retry/backoff and store via callback.
     */
    private void fetchAndStore(String url, String mimeType, long status,
                               WDRequest requestRef, WDResponseData response) {
        String bodyText = null;

        // Retry loop: getData may not be ready immediately after responseCompleted
        for (int attempt = 1; attempt <= GET_DATA_MAX_RETRIES; attempt++) {
            try {
                WDBytesValue bytesValue = driver.network().getData(
                        WDDataType.RESPONSE, requestRef, collector, false);

                if (bytesValue != null && bytesValue.getValue() != null) {
                    // WDBytesValue returns decoded string directly (type:"string")
                    bodyText = bytesValue.getValue();
                    break;
                }
            } catch (Exception e) {
                if (attempt < GET_DATA_MAX_RETRIES) {
                    long delay = GET_DATA_RETRY_BASE_MS
                            + (long) (Math.random() * (GET_DATA_RETRY_MAX_MS - GET_DATA_RETRY_BASE_MS));
                    LOG.fine("[NetworkIngestion] getData retry " + attempt + " for " + url
                            + " (delay=" + delay + "ms): " + e.getMessage());
                    try { Thread.sleep(delay); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                } else {
                    LOG.fine("[NetworkIngestion] getData failed after " + GET_DATA_MAX_RETRIES
                            + " attempts for " + url + ": " + e.getMessage());
                }
            }
        }

        // Disown the data to free browser memory
        try {
            driver.network().disownData(WDDataType.RESPONSE, collector, requestRef);
        } catch (Exception e) {
            LOG.fine("[NetworkIngestion] disownData failed for " + url + ": " + e.getMessage());
        }

        if (bodyText == null || bodyText.isEmpty()) {
            failedCount.incrementAndGet();
            return;
        }

        // Truncate if too large
        if (bodyText.length() > session.getMaxBytesPerDoc()) {
            bodyText = bodyText.substring(0, session.getMaxBytesPerDoc());
        }

        // Filter headers by allowlist
        Map<String, String> filteredHeaders = new LinkedHashMap<>();
        if (response.getHeaders() != null) {
            Set<String> allowlist = session.getHeaderAllowlist();
            for (WDHeader h : response.getHeaders()) {
                if (h.getName() != null && allowlist.contains(h.getName().toLowerCase())) {
                    String val = h.getValue() != null ? h.getValue().getValue() : "";
                    filteredHeaders.put(h.getName().toLowerCase(), val != null ? val : "");
                }
            }
        }

        // Store via callback
        if (callback != null) {
            try {
                String docId = callback.onBodyCaptured(
                        url, mimeType, status, bodyText, filteredHeaders, System.currentTimeMillis());
                if (docId != null) {
                    session.addArchivedDocId(docId);
                    capturedCount.incrementAndGet();
                    LOG.fine("[NetworkIngestion] Captured: " + url + " → docId=" + docId);
                } else {
                    failedCount.incrementAndGet();
                }
            } catch (Exception e) {
                failedCount.incrementAndGet();
                LOG.log(Level.FINE, "[NetworkIngestion] Callback failed for " + url, e);
            }
        }
    }

    // ── Filtering helpers ───────────────────────────────────────────

    private boolean isCaptureableMime(String mimeType) {
        if (mimeType == null) return false;
        String lower = mimeType.toLowerCase().split(";")[0].trim();
        return DEFAULT_CAPTURE_MIMES.contains(lower);
    }

    private boolean isExcludedUrl(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase();
        for (String pattern : EXCLUDED_URL_PATTERNS) {
            if (lower.contains(pattern)) return true;
        }
        return false;
    }

    // ── Status ──────────────────────────────────────────────────────

    public boolean isActive() { return active.get(); }
    public int getCapturedCount() { return capturedCount.get(); }
    public int getSkippedCount() { return skippedCount.get(); }
    public int getFailedCount() { return failedCount.get(); }
}
