package de.bund.zrb.mcpserver.research;

import de.bund.zrb.WebDriver;
import de.bund.zrb.event.WDNetworkEvent;
import de.bund.zrb.type.network.*;
import de.bund.zrb.type.network.WDCollector;
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
 * Passive Event-based Network Ingestion Pipeline for the Research Tool Suite.
 * <p>
 * Uses a {@code network.addDataCollector(RESPONSE)} to record response bodies
 * in the browser, then listens to {@code network.responseCompleted} events.
 * <b>No intercepts are used</b> – the browser is never blocked by this pipeline.
 * <p>
 * On every {@code responseCompleted}:
 * <ul>
 *   <li>Irrelevant responses (ads, tracking, images, CSS, JS, non-2xx, etc.)
 *       are immediately disowned via {@code disownData} to free browser memory.</li>
 *   <li>Relevant responses (text/html, application/json, etc.) are fetched
 *       asynchronously via {@code getData(disown=true)} and stored.</li>
 * </ul>
 * <p>
 * This passive approach avoids browser freezes that occurred with the previous
 * intercept-based design, where a failed or slow {@code continueResponse}
 * would permanently block the browser.
 */
/**
 * @deprecated Replaced by {@link DomSnapshotFetcher} and {@link CookieBannerDismisser}.
 * The passive event-based approach caused browser freezes. DOM snapshots are now
 * fetched via {@code script.evaluate("document.documentElement.outerHTML")} after
 * each navigation instead.
 * <p>
 * This class is retained for reference but all start() code is disabled.
 */
@Deprecated
public class NetworkIngestionPipeline {

    private static final Logger LOG = Logger.getLogger(NetworkIngestionPipeline.class.getName());

    // Default MIME types to capture body for
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

    // Hosts to never capture (tracking, ads, analytics, CDN junk)
    private static final List<String> EXCLUDED_HOST_PATTERNS = Arrays.asList(
            "googlesyndication.com", "googleadservices.com", "adtrafficquality.google",
            "doubleclick.net", "google-analytics.com", "googletagmanager.com",
            "analytics.", "pbd.yahoo.com", "yimg.com", "s.yimg.com",
            "tb.pbs.yahoo.com", "video-api.yql.yahoo.com",
            "ep2.adtrafficquality.google", "tpc.googlesyndication.com",
            "ads.yahoo.com", "pixel.", "tracking.", "ad.doubleclick.net",
            "facebook.com/tr", "connect.facebook.net", "cdn.segment.com",
            "bat.bing.com", "scorecardresearch.com", "quantserve.com",
            "amazon-adsystem.com", "taboola.com", "outbrain.com",
            "revcontent.com", "mgid.com", "nativo.com", "contentad.net",
            "gemini.yahoo.com", "geo.yahoo.com", "udc.yahoo.com",
            "consent.yahoo.com", "guce.yahoo.com", "b.scorecardresearch.com",
            "platform.twitter.com", "platform.instagram.com",
            "beap.gemini.yahoo.com", "csc.beap.gemini.yahoo.com",
            "comet.yahoo.com", "imasdk.googleapis.com"
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

    // ── HTML body cache for Jsoup-based link extraction ──
    private volatile String lastNavigationHtml;
    private volatile String lastNavigationUrl;

    // ── DevTools-style resource category counters ──
    private final ConcurrentHashMap<ResourceCategory, AtomicInteger> categoryCounts = new ConcurrentHashMap<>();

    // Tracks what the ingestion worker is currently doing (for watchdog diagnostics)
    private volatile String ingestionWorkerState = "idle";
    private volatile long ingestionWorkerStateTimestamp = 0;

    // Async ingestion (single-threaded to avoid overwhelming the browser)
    // Use a ThreadPoolExecutor so we can query the queue depth for diagnostics
    private final ThreadPoolExecutor ingestionExecutor = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<Runnable>(),
            r -> {
                Thread t = new Thread(r, "NetworkIngestion-worker");
                t.setDaemon(true);
                return t;
            }
    );

    // Periodic watchdog for ingestion pipeline – detects stuck getData/disownData calls
    private final ScheduledExecutorService ingestionWatchdog = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "NetworkIngestion-watchdog");
        t.setDaemon(true);
        return t;
    });

    // Callback for storing captured data
    private IngestionCallback callback;

    // Event listener reference (for cleanup)
    private Consumer<WDNetworkEvent.ResponseCompleted> responseCompletedListener;

    /**
     * Callback interface for the captured network data.
     */
    public interface IngestionCallback {
        String onBodyCaptured(String runId, String url, String mimeType, long status,
                              String bodyText, Map<String, String> headers, long capturedAt);
    }


    /**
     * Global default callback that plugins can register at startup.
     */
    private static volatile IngestionCallback globalDefaultCallback;

    public static void setGlobalDefaultCallback(IngestionCallback callback) {
        globalDefaultCallback = callback;
        LOG.info("[NetworkIngestion] Global default callback registered");
    }

    public static IngestionCallback getGlobalDefaultCallback() {
        return globalDefaultCallback;
    }

    public NetworkIngestionPipeline(WebDriver driver, ResearchSession session) {
        this.driver = driver;
        this.session = session;
    }

    /**
     * Start the pipeline: register DataCollector and subscribe to responseCompleted events.
     * No intercepts are used – the browser is never blocked.
     */
    public synchronized void start(IngestionCallback callback) {
//        if (active.get()) {
//            LOG.fine("[NetworkIngestion] Already active for session " + session.getSessionId());
//            return;
//        }
//
//        this.callback = callback;
//
//        try {
//            // 1. Register a response body collector (for getData after responseCompleted)
//            int maxSize = session.getMaxBytesPerDoc();
//            collector = driver.network().addResponseBodyCollector(maxSize);
//            LOG.info("[NetworkIngestion] DataCollector registered: " + collector.value()
//                    + " (maxSize=" + maxSize + ")");
//
//            // 2. Subscribe to responseCompleted events (for relevance check + body fetching)
//            WDSubscriptionRequest completedSubReq = new WDSubscriptionRequest(
//                    Collections.singletonList(WDEventNames.RESPONSE_COMPLETED.getName()));
//            responseCompletedListener = this::handleResponseCompleted;
//            driver.addEventListener(completedSubReq, responseCompletedListener);
//
//            active.set(true);
//            startIngestionWatchdog();
//            LOG.info("[NetworkIngestion] Passive pipeline started for session " + session.getSessionId());
//
//        } catch (Exception e) {
//            LOG.log(Level.WARNING, "[NetworkIngestion] Failed to start pipeline", e);
//            // Cleanup partial state
//            cleanup();
//        }
    }

    /**
     * Stop the pipeline: remove collector and unsubscribe from events.
     */
    public synchronized void stop() {
        if (!active.get()) return;
        active.set(false);
        cleanup();
        ingestionWatchdog.shutdownNow();
        ingestionExecutor.shutdown();
        LOG.info("[NetworkIngestion] Pipeline stopped. Captured=" + capturedCount.get()
                + " Skipped=" + skippedCount.get() + " Failed=" + failedCount.get());
    }

    private void cleanup() {
        // Remove event listener
        try {
            if (responseCompletedListener != null) {
                driver.removeEventListener(WDEventNames.RESPONSE_COMPLETED.getName(), responseCompletedListener);
                responseCompletedListener = null;
            }
        } catch (Exception e) {
            LOG.fine("[NetworkIngestion] Error removing responseCompleted listener: " + e.getMessage());
        }


        // Remove data collector
        try {
            if (collector != null) {
                driver.network().removeDataCollector(collector);
                collector = null;
            }
        } catch (Exception e) {
            LOG.fine("[NetworkIngestion] Error removing collector: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  RESPONSE_COMPLETED handler (relevance check + body fetching)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Called on every network.responseCompleted event.
     * Checks relevance directly from the event parameters (URL, MIME, status).
     * <ul>
     *   <li>Relevant → async fetch body via {@code getData(disown=true)}</li>
     *   <li>Irrelevant → async {@code disownData} to free browser memory</li>
     * </ul>
     * All heavy work is offloaded to {@code ingestionExecutor} so the
     * WebSocket event thread is never blocked.
     */
    private void handleResponseCompleted(WDNetworkEvent.ResponseCompleted event) {
        if (!active.get() || event == null) return;

        try {
            WDNetworkEvent.ResponseCompleted.ResponseCompletedParametersWD params = event.getParams();
            if (params == null || params.getRequest() == null) return;

            WDRequestData requestData = params.getRequest();
            WDResponseData response = params.getResponse();
            WDRequest requestRef = requestData.getRequest();
            if (requestRef == null) return;

            String url = response != null ? response.getUrl() : requestData.getUrl();
            String mimeType = response != null ? response.getMimeType() : null;
            long status = response != null ? response.getStatus() : 0;

            String urlShort = url != null && url.length() > 80 ? url.substring(0, 80) : url;

            // Classify for DevTools-style counters (lightweight, OK on event thread)
            classifyAndCount(response);

            // Check relevance
            boolean shouldCapture = isRelevantResponse(url, mimeType, status);

            if (shouldCapture) {
                // Async: fetch body with disown=true (auto-frees browser memory) and store
                final String fUrl = url;
                final String fUrlShort = urlShort;
                ingestionExecutor.submit(() -> {
                    ingestionWorkerState = "fetchAndStore:" + fUrlShort;
                    ingestionWorkerStateTimestamp = System.currentTimeMillis();
                    try {
                        fetchAndStore(fUrl, mimeType, status, requestRef, response);
                    } finally {
                        ingestionWorkerState = "idle";
                        ingestionWorkerStateTimestamp = System.currentTimeMillis();
                    }
                });
            } else {
                skippedCount.incrementAndGet();
                // Async: disown data to prevent DataCollector overflow
                final WDCollector col = collector; // capture stable reference
                final String dUrl = url;
                final String dUrlShort = urlShort;
                ingestionExecutor.submit(() -> {
                    try {
                        ingestionWorkerState = "disown:" + dUrlShort;
                        ingestionWorkerStateTimestamp = System.currentTimeMillis();
                        if (col != null) {
                            driver.network().disownData(WDDataType.RESPONSE, col, requestRef);
                        }
                    } catch (Exception e) {
                        LOG.fine("[NetworkIngestion] disownData (skip) failed for " + dUrl + ": " + e.getMessage());
                    } finally {
                        ingestionWorkerState = "idle";
                        ingestionWorkerStateTimestamp = System.currentTimeMillis();
                    }
                });
            }

        } catch (Exception e) {
            LOG.log(Level.FINE, "[NetworkIngestion] Error handling responseCompleted", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Body fetching + storage
    // ═══════════════════════════════════════════════════════════════

    private void fetchAndStore(String url, String mimeType, long status,
                               WDRequest requestRef, WDResponseData response) {
        String bodyText = null;
        String urlShort = url != null && url.length() > 80 ? url.substring(0, 80) : url;

        // Retry loop: getData may not be ready immediately after responseCompleted
        for (int attempt = 1; attempt <= GET_DATA_MAX_RETRIES; attempt++) {
            try {
                // disown=true: automatically frees browser memory after fetch
                WDBytesValue bytesValue = driver.network().getData(
                        WDDataType.RESPONSE, requestRef, collector, true);

                if (bytesValue != null && bytesValue.getValue() != null) {
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
                    // Safety: try to disown in case getData partially failed
                    try {
                        driver.network().disownData(WDDataType.RESPONSE, collector, requestRef);
                    } catch (Exception disownEx) {
                        LOG.fine("[NetworkIngestion] Safety disownData also failed for " + url);
                    }
                }
            }
        }

        if (bodyText == null || bodyText.isEmpty()) {
            failedCount.incrementAndGet();
            return;
        }

        // Truncate if too large
        if (bodyText.length() > session.getMaxBytesPerDoc()) {
            bodyText = bodyText.substring(0, session.getMaxBytesPerDoc());
        }

        // Cache HTML body for link extraction (navigation HTML only)
        String mimeLower = mimeType != null ? mimeType.toLowerCase() : "";
        if (mimeLower.contains("text/html") && status >= 200 && status < 300) {
            lastNavigationHtml = bodyText;
            lastNavigationUrl = url;
            LOG.fine("[NetworkIngestion] Cached HTML body: " + url + " (" + bodyText.length() + " chars)");
        }

        // Filter headers by allowlist
        Map<String, String> filteredHeaders = new LinkedHashMap<>();
        if (response != null && response.getHeaders() != null) {
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
                String runId = session.getRunId();
                String docId = callback.onBodyCaptured(
                        runId, url, mimeType, status, bodyText, filteredHeaders, System.currentTimeMillis());
                if (docId != null) {
                    session.addArchivedDocId(docId);
                    int count = capturedCount.incrementAndGet();
                    if (count <= 20) {
                        LOG.info("[NetworkIngestion] ✅ Captured #" + count + ": " + mimeType
                                + " " + url + " → id=" + docId
                                + " (" + bodyText.length() + " chars)");
                    } else {
                        LOG.fine("[NetworkIngestion] Captured: " + url + " → docId=" + docId);
                    }
                } else {
                    failedCount.incrementAndGet();
                }
            } catch (Exception e) {
                failedCount.incrementAndGet();
                LOG.log(Level.WARNING, "[NetworkIngestion] ❌ Callback failed for " + url, e);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Filtering helpers
    // ═══════════════════════════════════════════════════════════════

    private boolean isRelevantResponse(String url, String mimeType, long status) {
        // Status must be 2xx
        if (status < 200 || status >= 300) return false;

        // MIME type must be capturable
        if (!isCaptureableMime(mimeType)) return false;

        // Excluded URL patterns (auth/login)
        if (isExcludedUrl(url)) return false;

        // Domain policy
        if (!session.isUrlAllowed(url)) return false;

        return true;
    }

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
        try {
            String host = new java.net.URI(url).getHost();
            if (host != null) {
                String hostLower = host.toLowerCase();
                for (String excluded : EXCLUDED_HOST_PATTERNS) {
                    if (hostLower.contains(excluded) || lower.contains(excluded)) return true;
                }
            }
        } catch (Exception e) {
            for (String excluded : EXCLUDED_HOST_PATTERNS) {
                if (lower.contains(excluded)) return true;
            }
        }
        return false;
    }

    private void classifyAndCount(WDResponseData response) {
        if (response == null) return;
        ResourceCategory category = ResourceCategory.classify(response.getMimeType(), null);
        countCategory(category);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Status
    // ═══════════════════════════════════════════════════════════════

    public boolean isActive() { return active.get(); }
    public int getCapturedCount() { return capturedCount.get(); }
    public int getSkippedCount() { return skippedCount.get(); }
    public int getFailedCount() { return failedCount.get(); }

    // ── HTML body cache ─────────────────────────────────────────────

    public String getLastNavigationHtml() { return lastNavigationHtml; }
    public String getLastNavigationUrl() { return lastNavigationUrl; }
    public void setLastNavigationUrl(String url) { this.lastNavigationUrl = url; }

    public void clearNavigationCache() {
        lastNavigationHtml = null;
        lastNavigationUrl = null;
    }

    // ── Resource category counters ──────────────────────────────────

    void countCategory(ResourceCategory cat) {
        categoryCounts.computeIfAbsent(cat, k -> new AtomicInteger(0)).incrementAndGet();
    }

    public int getCategoryCount(ResourceCategory cat) {
        AtomicInteger c = categoryCounts.get(cat);
        return c != null ? c.get() : 0;
    }

    public Map<String, Integer> getCategoryCounts() {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (ResourceCategory cat : ResourceCategory.values()) {
            int c = getCategoryCount(cat);
            if (c > 0) result.put(cat.name(), c);
        }
        return result;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Ingestion Pipeline Watchdog
    // ═══════════════════════════════════════════════════════════════

    /**
     * Starts a periodic watchdog that monitors the ingestion worker thread.
     * If the worker is stuck in a getData/disownData call for more than 10 seconds,
     * it prints a warning with the current state. This helps diagnose browser freezes.
     */
    private void startIngestionWatchdog() {
        ingestionWatchdog.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!active.get()) return;
                    int queueSize = ingestionExecutor.getQueue().size();
                    int activeCount = ingestionExecutor.getActiveCount();
                    String workerState = ingestionWorkerState;
                    long stateAge = ingestionWorkerStateTimestamp > 0
                            ? System.currentTimeMillis() - ingestionWorkerStateTimestamp : 0;

                    if (activeCount > 0 && stateAge > 10_000) {
                        LOG.warning("[INGESTION WATCHDOG] Worker STUCK for " + stateAge + " ms!"
                                + " state='" + workerState + "'"
                                + " queueDepth=" + queueSize
                                + " captured=" + capturedCount.get()
                                + " skipped=" + skippedCount.get()
                                + " failed=" + failedCount.get());
                    } else if (queueSize > 5) {
                        LOG.info("[INGESTION WATCHDOG] Queue building up: " + queueSize
                                + " items, active=" + activeCount
                                + " state='" + workerState + "'"
                                + " stateAge=" + stateAge + "ms");
                    }
                } catch (Exception e) {
                    // Watchdog must never crash
                }
            }
        }, 3000, 3000, TimeUnit.MILLISECONDS);
    }
}
