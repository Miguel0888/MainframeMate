package de.bund.zrb.mcpserver.research;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe tracker for visited URLs during a research session.
 * URLs are canonicalized before storage to avoid duplicates caused by
 * trailing slashes, fragments, or case differences.
 */
public class VisitedUrlTracker {

    private final Set<String> visited = ConcurrentHashMap.newKeySet();

    /**
     * Mark a URL as visited.
     *
     * @return true if the URL was newly added, false if already visited
     */
    public boolean markVisited(String url) {
        return visited.add(UrlCanonicalizer.canonicalize(url));
    }

    /**
     * Check whether a URL has been visited.
     */
    public boolean isVisited(String url) {
        return visited.contains(UrlCanonicalizer.canonicalize(url));
    }

    /**
     * Returns the number of distinct visited URLs.
     */
    public int getVisitedCount() {
        return visited.size();
    }

    /**
     * Returns an unmodifiable view of all visited canonical URLs.
     */
    public Set<String> getAll() {
        return Collections.unmodifiableSet(visited);
    }

    /**
     * Clear all visited URLs (e.g. on session reset).
     */
    public void clear() {
        visited.clear();
    }
}
