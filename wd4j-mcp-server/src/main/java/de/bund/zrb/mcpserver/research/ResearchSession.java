package de.bund.zrb.mcpserver.research;

import de.bund.zrb.type.script.WDRemoteReference;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Holds the full state of a research session as specified in the requirements:
 * <ul>
 *   <li>Session identity: sessionId, userContextId, contextIds</li>
 *   <li>View management: viewToken, menuItemId→SharedRef mapping</li>
 *   <li>Policy: domainPolicy, limits, privacyPolicy, settlePolicy defaults</li>
 *   <li>Crawl queue tracking: newArchivedDocIds since last menu call</li>
 * </ul>
 */
public class ResearchSession {

    private static final Logger LOG = Logger.getLogger(ResearchSession.class.getName());

    // ── Identity ────────────────────────────────────────────────────

    public enum Mode { RESEARCH, AGENT }

    private final String sessionId;
    private final Mode mode;
    private String userContextId;
    private String runId;  // Data Lake run ID
    private final List<String> contextIds = new CopyOnWriteArrayList<>();

    // ── View Token management ───────────────────────────────────────

    private final AtomicInteger viewTokenCounter = new AtomicInteger(0);
    private volatile String currentViewToken;
    private volatile MenuView currentMenuView;
    private final Map<String, WDRemoteReference.SharedReference> menuItemRefs =
            Collections.synchronizedMap(new LinkedHashMap<String, WDRemoteReference.SharedReference>());

    // ── Policy & Config ─────────────────────────────────────────────

    private SettlePolicy defaultSettlePolicy = SettlePolicy.NAVIGATION;
    private int maxMenuItems = 12;
    private int excerptMaxLength = 500;

    // Domain policy
    private final Set<String> domainInclude = new LinkedHashSet<>();
    private final Set<String> domainExclude = new LinkedHashSet<>();

    // Limits
    private int maxUrls = 500;
    private int maxDepth = 5;
    private int maxBytesPerDoc = 2_000_000; // 2 MB

    // Privacy policy: which headers to capture
    private final Set<String> headerAllowlist = new LinkedHashSet<>(Arrays.asList(
            "content-type", "content-length", "last-modified", "etag", "cache-control"
    ));
    private boolean captureRequestBodies = false;

    // ── Archived docs tracking ──────────────────────────────────────

    private final List<String> newArchivedDocIds = new CopyOnWriteArrayList<>();
    private String crawlQueueId;

    // ── Network Ingestion Pipeline ────────────────────────────────

    private NetworkIngestionPipeline networkPipeline;

    public NetworkIngestionPipeline getNetworkPipeline() { return networkPipeline; }
    public void setNetworkPipeline(NetworkIngestionPipeline pipeline) { this.networkPipeline = pipeline; }

    // ── Last Navigation URL (for same-URL detection, no pipeline needed) ──

    private volatile String lastNavigationUrl;

    public String getLastNavigationUrl() { return lastNavigationUrl; }
    public void setLastNavigationUrl(String url) { this.lastNavigationUrl = url; }

    // ── Browser History Tracking ─────────────────────────────────
    // Tracks the position in the browser's history stack to prevent
    // traverseHistory(-1) when already at the beginning (which freezes the browser).

    private int historyIndex = 0;   // current position (0 = first page)
    private int historySize  = 0;   // total entries in history

    /** Called after a successful URL navigation (not back/forward). */
    public void historyPush() {
        // New navigation truncates any forward entries
        historyIndex++;
        historySize = historyIndex + 1;
    }

    /** @return true if back is possible (historyIndex > 0) */
    public boolean canGoBack() { return historyIndex > 0; }

    /** @return true if forward is possible (entries ahead of current position) */
    public boolean canGoForward() { return historyIndex < historySize - 1; }

    /** Called after a successful back navigation. */
    public void historyBack() { if (historyIndex > 0) historyIndex--; }

    /** Called after a successful forward navigation. */
    public void historyForward() { if (historyIndex < historySize - 1) historyIndex++; }

    /** Reset history tracking (e.g. on browser restart). */
    public void historyReset() { historyIndex = 0; historySize = 0; }

    // ── Constructor ─────────────────────────────────────────────────

    public ResearchSession(String sessionId, Mode mode) {
        this.sessionId = sessionId;
        this.mode = mode;
    }

    // ═══════════════════════════════════════════════════════════════
    //  View Token management
    // ═══════════════════════════════════════════════════════════════

    /**
     * Generate the next view token without storing a view yet.
     * Allows caller to create a MenuView with the token embedded,
     * then store it via {@link #setCurrentView(MenuView)}.
     */
    public synchronized String generateNextViewToken() {
        return "v" + viewTokenCounter.incrementAndGet();
    }

    /**
     * Store the final MenuView (with viewToken already set) as the current view.
     * This replaces the old pattern where updateView stored an object with null token.
     */
    public synchronized void setCurrentView(MenuView menuView) {
        this.currentViewToken = menuView.getViewToken();
        this.currentMenuView = menuView;
        this.menuItemRefs.clear();
        LOG.fine("[ResearchSession " + sessionId + "] View set → " + currentViewToken);
    }

    /**
     * Update the current view with a new MenuView and the corresponding
     * menuItemId → SharedReference mapping.
     * Generates a new viewToken and invalidates all previous menuItem refs.
     * @deprecated Use {@link #generateNextViewToken()} + {@link #setCurrentView(MenuView)} instead.
     */
    public synchronized String updateView(MenuView menuView,
                                          Map<String, WDRemoteReference.SharedReference> itemRefs) {
        String newToken = "v" + viewTokenCounter.incrementAndGet();
        this.currentViewToken = newToken;
        this.currentMenuView = menuView;
        this.menuItemRefs.clear();
        if (itemRefs != null) {
            this.menuItemRefs.putAll(itemRefs);
        }
        LOG.fine("[ResearchSession " + sessionId + "] View updated → " + newToken
                + " (" + menuItemRefs.size() + " items)");
        return newToken;
    }

    public boolean isViewTokenValid(String viewToken) {
        return currentViewToken != null && currentViewToken.equals(viewToken);
    }

    /**
     * Resolve a menuItemId to its SharedReference for action dispatch.
     *
     * @throws IllegalArgumentException if the menuItemId is unknown
     * @throws IllegalStateException    if the viewToken is stale
     */
    public synchronized WDRemoteReference.SharedReference resolveMenuItem(
            String menuItemId, String viewToken) {
        if (!isViewTokenValid(viewToken)) {
            throw new IllegalStateException(
                    "Stale viewToken '" + viewToken + "' (current: " + currentViewToken + "). "
                  + "Call research_menu to get the current view before choosing.");
        }
        WDRemoteReference.SharedReference ref = menuItemRefs.get(menuItemId);
        if (ref == null) {
            throw new IllegalArgumentException(
                    "Unknown menuItemId '" + menuItemId + "'. "
                  + "Available: " + menuItemRefs.keySet());
        }
        return ref;
    }

    public synchronized void invalidateView() {
        currentViewToken = null;
        currentMenuView = null;
        menuItemRefs.clear();
    }

    // ═══════════════════════════════════════════════════════════════
    //  Archived doc tracking
    // ═══════════════════════════════════════════════════════════════

    /** Add a newly archived doc ID (called by the ingestion pipeline). */
    public void addArchivedDocId(String docId) {
        newArchivedDocIds.add(docId);
    }

    /** Drain and return all new archived doc IDs since last call. */
    public List<String> drainNewArchivedDocIds() {
        List<String> drained = new ArrayList<>(newArchivedDocIds);
        newArchivedDocIds.clear();
        return drained;
    }

    // ═══════════════════════════════════════════════════════════════
    //  Domain policy
    // ═══════════════════════════════════════════════════════════════

    /** Check if a URL is allowed by domain policy. */
    public boolean isUrlAllowed(String url) {
        if (url == null) return false;
        String host = extractHost(url);
        if (host == null) return true; // can't check → allow

        // Exclude takes precedence
        for (String ex : domainExclude) {
            if (host.contains(ex)) return false;
        }
        // If include list is non-empty, host must match
        if (!domainInclude.isEmpty()) {
            for (String inc : domainInclude) {
                if (host.contains(inc)) return true;
            }
            return false;
        }
        return true;
    }

    private String extractHost(String url) {
        try {
            int schemeEnd = url.indexOf("://");
            if (schemeEnd < 0) return null;
            String rest = url.substring(schemeEnd + 3);
            int slash = rest.indexOf('/');
            return slash > 0 ? rest.substring(0, slash).toLowerCase() : rest.toLowerCase();
        } catch (Exception e) {
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Getters
    // ═══════════════════════════════════════════════════════════════

    public String getSessionId() { return sessionId; }
    public Mode getMode() { return mode; }
    public String getUserContextId() { return userContextId; }
    public String getRunId() { return runId; }
    public List<String> getContextIds() { return Collections.unmodifiableList(contextIds); }
    public String getCurrentViewToken() { return currentViewToken; }
    public MenuView getCurrentMenuView() { return currentMenuView; }
    public SettlePolicy getDefaultSettlePolicy() { return defaultSettlePolicy; }
    public int getMaxMenuItems() { return maxMenuItems; }
    public int getExcerptMaxLength() { return excerptMaxLength; }
    public Set<String> getDomainInclude() { return domainInclude; }
    public Set<String> getDomainExclude() { return domainExclude; }
    public int getMaxUrls() { return maxUrls; }
    public int getMaxDepth() { return maxDepth; }
    public int getMaxBytesPerDoc() { return maxBytesPerDoc; }
    public Set<String> getHeaderAllowlist() { return headerAllowlist; }
    public boolean isCaptureRequestBodies() { return captureRequestBodies; }
    public String getCrawlQueueId() { return crawlQueueId; }

    // ═══════════════════════════════════════════════════════════════
    //  Setters (config)
    // ═══════════════════════════════════════════════════════════════

    public void setUserContextId(String id) { this.userContextId = id; }
    public void setRunId(String runId) { this.runId = runId; }
    public void addContextId(String id) { contextIds.add(id); }
    public void setDefaultSettlePolicy(SettlePolicy p) { this.defaultSettlePolicy = p; }
    public void setMaxMenuItems(int max) { this.maxMenuItems = max; }
    public void setExcerptMaxLength(int max) { this.excerptMaxLength = max; }
    public void setMaxUrls(int max) { this.maxUrls = max; }
    public void setMaxDepth(int max) { this.maxDepth = max; }
    public void setMaxBytesPerDoc(int max) { this.maxBytesPerDoc = max; }
    public void setCaptureRequestBodies(boolean b) { this.captureRequestBodies = b; }
    public void setCrawlQueueId(String id) { this.crawlQueueId = id; }
}
