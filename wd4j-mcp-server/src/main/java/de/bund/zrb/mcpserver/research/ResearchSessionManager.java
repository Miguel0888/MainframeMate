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

    private ResearchSessionManager() {}

    public static ResearchSessionManager getInstance() {
        return INSTANCE;
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
     */
    public void remove(BrowserSession browserSession) {
        ResearchSession removed = sessions.remove(browserSession);
        if (removed != null) {
            LOG.info("[ResearchSessionManager] Removed session " + removed.getSessionId());
        }
    }

    /**
     * Clear all sessions (e.g. on app shutdown).
     */
    public void clear() {
        sessions.clear();
    }
}

