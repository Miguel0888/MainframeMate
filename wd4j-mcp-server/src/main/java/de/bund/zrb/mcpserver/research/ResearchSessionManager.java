package de.bund.zrb.mcpserver.research;

import de.bund.zrb.mcpserver.browser.BrowserSession;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Manages {@link ResearchSession} instances.
 * Each {@link BrowserSession} has at most one associated ResearchSession.
 */
public class ResearchSessionManager {

    private static final Logger LOG = Logger.getLogger(ResearchSessionManager.class.getName());

    private static final ResearchSessionManager INSTANCE = new ResearchSessionManager();

    private final Map<BrowserSession, ResearchSession> sessions = new ConcurrentHashMap<>();

    /** Global callback for Data Lake run lifecycle, registered by WebSearchPlugin. */
    private static volatile RunLifecycleCallback runLifecycleCallback;

    /** Global callback for snapshot archiving, registered by WebSearchPlugin. */
    private static volatile SnapshotArchivingCallback snapshotArchivingCallback;

    private ResearchSessionManager() {}

    public static ResearchSessionManager getInstance() {
        return INSTANCE;
    }

    /**
     * Register a global callback for Data Lake run lifecycle events.
     * Called once at plugin init time (e.g. from WebSearchPlugin).
     */
    public static void setRunLifecycleCallback(RunLifecycleCallback callback) {
        runLifecycleCallback = callback;
        LOG.info("[ResearchSessionManager] RunLifecycleCallback registered");
    }

    /**
     * Get the global RunLifecycleCallback, or null if none registered.
     */
    public static RunLifecycleCallback getRunLifecycleCallback() {
        return runLifecycleCallback;
    }

    /**
     * Register a global callback for DOM snapshot archiving.
     * Called once at plugin init time (e.g. from WebSearchPlugin).
     */
    public static void setSnapshotArchivingCallback(SnapshotArchivingCallback callback) {
        snapshotArchivingCallback = callback;
        LOG.info("[ResearchSessionManager] SnapshotArchivingCallback registered");
    }

    /**
     * Get the global SnapshotArchivingCallback, or null if none registered.
     */
    public static SnapshotArchivingCallback getSnapshotArchivingCallback() {
        return snapshotArchivingCallback;
    }

    /**
     * Get the existing ResearchSession for the given BrowserSession, or null if none exists.
     */
    public ResearchSession get(BrowserSession browserSession) {
        return sessions.get(browserSession);
    }

    /**
     * Get or create a ResearchSession for the given BrowserSession.
     * Default mode: RESEARCH.
     */
    public ResearchSession getOrCreate(BrowserSession browserSession) {
        return getOrCreate(browserSession, ResearchSession.Mode.RESEARCH);
    }

    /**
     * Get or create a ResearchSession for the given BrowserSession with specified mode.
     */
    public ResearchSession getOrCreate(BrowserSession browserSession, ResearchSession.Mode mode) {
        ResearchSession existing = sessions.get(browserSession);
        if (existing != null) {
            return existing;
        }

        String sessionId = UUID.randomUUID().toString().substring(0, 8);
        ResearchSession rs = new ResearchSession(sessionId, mode);
        sessions.put(browserSession, rs);
        LOG.info("[ResearchSessionManager] Created session " + sessionId + " (mode=" + mode + ")");
        return rs;
    }

    /**
     * Remove the ResearchSession for the given BrowserSession (e.g. on close).
     * Ends the Data Lake run.
     */
    public void remove(BrowserSession browserSession) {
        ResearchSession removed = sessions.remove(browserSession);
        if (removed != null) {
            // End Data Lake run
            endRunForSession(removed);
            LOG.info("[ResearchSessionManager] Removed session " + removed.getSessionId());
        }
    }

    /**
     * Clear all sessions (e.g. on app shutdown).
     * Ends all Data Lake runs.
     */
    public void clear() {
        for (ResearchSession rs : sessions.values()) {
            endRunForSession(rs);
        }
        sessions.clear();
    }

    /**
     * End the Data Lake run for a session via callback.
     */
    private void endRunForSession(ResearchSession rs) {
        if (rs.getRunId() != null && runLifecycleCallback != null) {
            try {
                runLifecycleCallback.endRun(rs.getRunId());
                LOG.info("[ResearchSessionManager] Data Lake run ended: " + rs.getRunId());
            } catch (Exception e) {
                LOG.warning("[ResearchSessionManager] Error ending run: " + e.getMessage());
            }
        }
    }
}

