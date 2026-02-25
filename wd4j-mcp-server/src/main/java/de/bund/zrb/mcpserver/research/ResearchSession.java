package de.bund.zrb.mcpserver.research;

import de.bund.zrb.type.script.WDRemoteReference;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Holds the state of a research session:
 * - current viewToken
 * - mapping of menuItemId → SharedReference (for action dispatch)
 * - session config (mode, settle policy defaults, limits)
 *
 * <p>The viewToken is incremented on every {@link #updateView(MenuView, Map)} call.
 * A stale viewToken in a choose/action request must be rejected.</p>
 */
public class ResearchSession {

    private static final Logger LOG = Logger.getLogger(ResearchSession.class.getName());

    public enum Mode { RESEARCH, AGENT }

    private final String sessionId;
    private final Mode mode;
    private final AtomicInteger viewTokenCounter = new AtomicInteger(0);

    // Current state
    private volatile String currentViewToken;
    private volatile MenuView currentMenuView;
    private final Map<String, WDRemoteReference.SharedReference> menuItemRefs = new LinkedHashMap<>();

    // Config defaults
    private SettlePolicy defaultSettlePolicy = SettlePolicy.NAVIGATION;
    private int maxMenuItems = 50;
    private int excerptMaxLength = 3000;

    public ResearchSession(String sessionId, Mode mode) {
        this.sessionId = sessionId;
        this.mode = mode;
    }

    // ── View Token management ───────────────────────────────────────

    /**
     * Update the current view with a new MenuView and the corresponding
     * menuItemId → SharedReference mapping.
     * Generates a new viewToken and invalidates all previous menuItem refs.
     */
    public synchronized String updateView(MenuView menuView, Map<String, WDRemoteReference.SharedReference> itemRefs) {
        String newToken = "v" + viewTokenCounter.incrementAndGet();
        this.currentViewToken = newToken;
        this.currentMenuView = menuView;
        this.menuItemRefs.clear();
        if (itemRefs != null) {
            this.menuItemRefs.putAll(itemRefs);
        }
        LOG.fine("[ResearchSession] View updated → " + newToken + " (" + menuItemRefs.size() + " items)");
        return newToken;
    }

    /**
     * Validate that the given viewToken matches the current one.
     *
     * @param viewToken token from the bot's request
     * @return true if valid, false if stale
     */
    public boolean isViewTokenValid(String viewToken) {
        return currentViewToken != null && currentViewToken.equals(viewToken);
    }

    /**
     * Resolve a menuItemId to its SharedReference for action dispatch.
     *
     * @throws IllegalArgumentException if the menuItemId is unknown
     * @throws IllegalStateException    if the viewToken is stale
     */
    public synchronized WDRemoteReference.SharedReference resolveMenuItem(String menuItemId, String viewToken) {
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

    /**
     * Invalidate the current view (e.g. after external navigation).
     */
    public synchronized void invalidateView() {
        currentViewToken = null;
        currentMenuView = null;
        menuItemRefs.clear();
    }

    // ── Getters ─────────────────────────────────────────────────────

    public String getSessionId() { return sessionId; }
    public Mode getMode() { return mode; }
    public String getCurrentViewToken() { return currentViewToken; }
    public MenuView getCurrentMenuView() { return currentMenuView; }
    public SettlePolicy getDefaultSettlePolicy() { return defaultSettlePolicy; }
    public int getMaxMenuItems() { return maxMenuItems; }
    public int getExcerptMaxLength() { return excerptMaxLength; }

    // ── Setters (config) ────────────────────────────────────────────

    public void setDefaultSettlePolicy(SettlePolicy policy) { this.defaultSettlePolicy = policy; }
    public void setMaxMenuItems(int max) { this.maxMenuItems = max; }
    public void setExcerptMaxLength(int max) { this.excerptMaxLength = max; }
}

