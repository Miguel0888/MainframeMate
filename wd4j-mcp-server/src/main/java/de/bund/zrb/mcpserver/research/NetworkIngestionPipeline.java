package de.bund.zrb.mcpserver.research;

import de.bund.zrb.WebDriver;
import de.bund.zrb.command.request.parameters.network.AddInterceptParameters;
import de.bund.zrb.command.response.WDNetworkResult;
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
 * Intercept-based Network Ingestion Pipeline for the Research Tool Suite.
 * <p>
 * Uses {@code network.addIntercept(RESPONSE_STARTED)} to block every response
 * at header-arrival time. The pipeline inspects MIME type, URL, and status:
 * <ul>
 *   <li>Irrelevant responses (ads, tracking, images, CSS, JS, etc.) are immediately
 *       released via {@code continueResponse} – the browser is never overloaded.</li>
 *   <li>Relevant responses (text/html, application/json, etc.) are also released
 *       via {@code continueResponse}, but additionally the body is fetched
 *       asynchronously after {@code responseCompleted} via DataCollector + getData.</li>
 * </ul>
 * <p>
 * This approach avoids the browser freeze caused by the old passive DataCollector,
 * because the browser is blocked per-response and released immediately after inspection.
 * No response backlog can accumulate.
 */
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
    private String interceptId;
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

    // Track requests that are relevant → fetch body on responseCompleted
    private final ConcurrentHashMap<String, PendingCapture> pendingCaptures = new ConcurrentHashMap<>();

    // Executor for intercept handling (continueResponse must NOT run in the WebSocket thread
    // because sendAndWaitForResponse blocks, causing a deadlock with the single-threaded WS reader)
    private final ExecutorService interceptExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "NetworkIngestion-intercept");
        t.setDaemon(true);
        return t;
    });

    // Async ingestion (single-threaded to avoid overwhelming the browser)
    private final ExecutorService ingestionExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "NetworkIngestion-worker");
        t.setDaemon(true);
        return t;
    });

    // Callback for storing captured data
    private IngestionCallback callback;

    // Event listener references (for cleanup)
    private Consumer<WDNetworkEvent.ResponseStarted> responseStartedListener;
    private Consumer<WDNetworkEvent.ResponseCompleted> responseCompletedListener;

    /**
     * Callback interface for the captured network data.
     */
    public interface IngestionCallback {
        String onBodyCaptured(String runId, String url, String mimeType, long status,
                              String bodyText, Map<String, String> headers, long capturedAt);
    }

    /** Pending capture metadata (from responseStarted, used in responseCompleted). */
    private static class PendingCapture {
        final String url;
        final String mimeType;
        final long status;
        final WDResponseData response;

        PendingCapture(String url, String mimeType, long status, WDResponseData response) {
            this.url = url;
            this.mimeType = mimeType;
            this.status = status;
            this.response = response;
        }
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
     * Start the pipeline: register intercept on RESPONSE_STARTED and subscribe to events.
     */
    public synchronized void start(IngestionCallback callback) {
        if (active.get()) {
            LOG.fine("[NetworkIngestion] Already active for session " + session.getSessionId());
            return;
        }

        this.callback = callback;

        try {
            // 1. Register a response body collector (for getData after responseCompleted)
            int maxSize = session.getMaxBytesPerDoc();
            collector = driver.network().addResponseBodyCollector(maxSize);
            LOG.info("[NetworkIngestion] DataCollector registered: " + collector.value()
                    + " (maxSize=" + maxSize + ")");

            // 2. Add intercept on RESPONSE_STARTED phase
            //    This blocks every response at header time until we call continueResponse.
            WDNetworkResult.AddInterceptResult interceptResult = driver.network().addIntercept(
                    Collections.singletonList(AddInterceptParameters.InterceptPhase.RESPONSE_STARTED));
            interceptId = interceptResult.getIntercept().value();
            LOG.info("[NetworkIngestion] Intercept registered: " + interceptId);

            // 3. Subscribe to responseStarted events (for intercept handling)
            WDSubscriptionRequest startedSubReq = new WDSubscriptionRequest(
                    Collections.singletonList(WDEventNames.RESPONSE_STARTED.getName()));
            responseStartedListener = this::handleResponseStarted;
            driver.addEventListener(startedSubReq, responseStartedListener);

            // 4. Subscribe to responseCompleted events (for body fetching)
            WDSubscriptionRequest completedSubReq = new WDSubscriptionRequest(
                    Collections.singletonList(WDEventNames.RESPONSE_COMPLETED.getName()));
            responseCompletedListener = this::handleResponseCompleted;
            driver.addEventListener(completedSubReq, responseCompletedListener);

            active.set(true);
            LOG.info("[NetworkIngestion] Intercept-based pipeline started for session " + session.getSessionId());

        } catch (Exception e) {
            LOG.log(Level.WARNING, "[NetworkIngestion] Failed to start pipeline", e);
            // Cleanup partial state
            cleanup();
        }
    }

    /**
     * Stop the pipeline: remove intercept, collector, and unsubscribe.
     */
    public synchronized void stop() {
        if (!active.get()) return;
        active.set(false);
        cleanup();
        interceptExecutor.shutdown();
        ingestionExecutor.shutdown();
        pendingCaptures.clear();
        LOG.info("[NetworkIngestion] Pipeline stopped. Captured=" + capturedCount.get()
                + " Skipped=" + skippedCount.get() + " Failed=" + failedCount.get());
    }

    private void cleanup() {
        // Remove event listeners
        try {
            if (responseStartedListener != null) {
                driver.removeEventListener(WDEventNames.RESPONSE_STARTED.getName(), responseStartedListener);
                responseStartedListener = null;
            }
        } catch (Exception e) {
            LOG.fine("[NetworkIngestion] Error removing responseStarted listener: " + e.getMessage());
        }
        try {
            if (responseCompletedListener != null) {
                driver.removeEventListener(WDEventNames.RESPONSE_COMPLETED.getName(), responseCompletedListener);
                responseCompletedListener = null;
            }
        } catch (Exception e) {
            LOG.fine("[NetworkIngestion] Error removing responseCompleted listener: " + e.getMessage());
        }

        // Remove intercept
        try {
            if (interceptId != null) {
                driver.network().removeIntercept(interceptId);
                interceptId = null;
            }
        } catch (Exception e) {
            LOG.fine("[NetworkIngestion] Error removing intercept: " + e.getMessage());
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
    //  RESPONSE_STARTED handler (intercept decision point)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Called on every network.responseStarted event.
     * Since we have an intercept on RESPONSE_STARTED, the response is BLOCKED.
     * We must ALWAYS call continueResponse (or failRequest) to release it.
     *
     * Decision: if the response is relevant (capturable MIME, allowed URL, 2xx),
     * we remember it for body capture on responseCompleted. Either way,
     * we IMMEDIATELY call continueResponse to unblock the browser.
     *
     * IMPORTANT: This handler is called from the WebSocket reader thread.
     * continueResponse() uses sendAndWaitForResponse() which blocks synchronously.
     * If we block the WS reader thread, the response to continueResponse can never
     * arrive → deadlock. Therefore we delegate to interceptExecutor.
     */
    private void handleResponseStarted(WDNetworkEvent.ResponseStarted event) {
        if (event == null) return;
        // Delegate to interceptExecutor to avoid blocking the WebSocket reader thread
        interceptExecutor.submit(() -> handleResponseStartedImpl(event));
    }

    /** Actual responseStarted logic, runs in interceptExecutor thread. */
    private void handleResponseStartedImpl(WDNetworkEvent.ResponseStarted event) {
        String requestId = null;
        try {
            WDNetworkEvent.ResponseStarted.ResponseStartedParametersWD params = event.getParams();
            if (params == null || params.getRequest() == null) return;

            WDRequestData requestData = params.getRequest();
            WDResponseData response = params.getResponse();
            requestId = requestData.getRequest() != null ? requestData.getRequest().value() : null;

            if (requestId == null) return;

            // Only handle if this event was actually blocked by our intercept
            if (!params.isBlocked()) {
                // Not blocked → no need to continue, just classify
                classifyAndCount(response);
                return;
            }

            String url = response != null ? response.getUrl() : requestData.getUrl();
            String mimeType = response != null ? response.getMimeType() : null;
            long status = response != null ? response.getStatus() : 0;

            // Classify for DevTools-style counters
            classifyAndCount(response);

            // Check if this response is worth capturing
            boolean shouldCapture = isRelevantResponse(url, mimeType, status);

            if (shouldCapture) {
                // Remember for body capture on responseCompleted
                pendingCaptures.put(requestId, new PendingCapture(url, mimeType, status, response));
                LOG.fine("[NetworkIngestion] Marked for capture: " + mimeType + " " + url);
            } else {
                skippedCount.incrementAndGet();
            }

            // ALWAYS continue the response immediately to unblock the browser
            driver.network().continueResponse(requestId);

        } catch (Exception e) {
            LOG.log(Level.WARNING, "[NetworkIngestion] Error in responseStarted handler", e);
            // Safety: always try to continue to prevent browser deadlock
            if (requestId != null) {
                try {
                    driver.network().continueResponse(requestId);
                } catch (Exception e2) {
                    LOG.warning("[NetworkIngestion] Failed to continueResponse on error: " + e2.getMessage());
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  RESPONSE_COMPLETED handler (body fetching)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Called on every network.responseCompleted event.
     * If this request was marked as relevant in responseStarted,
     * fetch the body asynchronously via DataCollector + getData.
     */
    private void handleResponseCompleted(WDNetworkEvent.ResponseCompleted event) {
        if (!active.get() || event == null) return;

        try {
            WDNetworkEvent.ResponseCompleted.ResponseCompletedParametersWD params = event.getParams();
            if (params == null || params.getRequest() == null) return;

            WDRequestData requestData = params.getRequest();
            String requestId = requestData.getRequest() != null ? requestData.getRequest().value() : null;
            if (requestId == null) return;

            // Check if this was marked for capture
            PendingCapture pending = pendingCaptures.remove(requestId);
            if (pending == null) return; // Not relevant, skip

            WDResponseData response = params.getResponse();
            WDRequest requestRef = requestData.getRequest();

            // Async: fetch body and store
            ingestionExecutor.submit(() -> fetchAndStore(
                    pending.url, pending.mimeType, pending.status,
                    requestRef, response != null ? response : pending.response));

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

        // Retry loop: getData may not be ready immediately
        for (int attempt = 1; attempt <= GET_DATA_MAX_RETRIES; attempt++) {
            try {
                WDBytesValue bytesValue = driver.network().getData(
                        WDDataType.RESPONSE, requestRef, collector, false);

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
}
