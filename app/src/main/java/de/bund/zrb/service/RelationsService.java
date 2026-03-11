package de.bund.zrb.service;

import de.bund.zrb.ui.drawer.LeftDrawer.RelationEntry;
import de.bund.zrb.wiki.domain.WikiCredentials;
import de.bund.zrb.wiki.domain.WikiSiteId;
import de.bund.zrb.wiki.port.WikiContentService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service that resolves and caches relations (e.g. outgoing wiki links) for
 * the currently active tab.  Results are cached by tab path so that switching
 * tabs does not trigger redundant HTTP requests.
 *
 * <p>Uses the prefetch thread pool (configurable concurrency) for background
 * resolution so the EDT is never blocked.</p>
 */
public class RelationsService {

    private static final Logger LOG = Logger.getLogger(RelationsService.class.getName());

    /** In-memory cache: tabPath → resolved relations.  O(1) lookup. */
    private final ConcurrentHashMap<String, List<RelationEntry>> cache =
            new ConcurrentHashMap<String, List<RelationEntry>>();

    private final ExecutorService pool;
    private volatile WikiContentService wikiService;

    /** Currently running resolution task (cancel on tab switch). */
    private volatile Future<?> currentTask;

    public RelationsService(int concurrency) {
        int poolSize = Math.max(1, Math.min(concurrency, 4));
        this.pool = Executors.newFixedThreadPool(poolSize);
    }

    public void setWikiService(WikiContentService wikiService) {
        this.wikiService = wikiService;
    }

    // ════════════════════════════════════════════════════════════
    //  Public API
    // ════════════════════════════════════════════════════════════

    /**
     * Get cached relations for the given tab path, or null if not yet resolved.
     */
    public List<RelationEntry> getCached(String tabPath) {
        return cache.get(tabPath);
    }

    /**
     * Resolve wiki link relations for a page in the background.
     * The callback runs on the EDT with the result.
     *
     * @param siteId    wiki site id
     * @param pageTitle page title
     * @param tabPath   unique tab path (used as cache key)
     * @param callback  called on EDT with resolved relations
     */
    public void resolveWikiLinks(final WikiSiteId siteId,
                                 final String pageTitle,
                                 final String tabPath,
                                 final RelationsCallback callback) {
        // Cancel any previously running task
        cancelCurrent();

        // Check cache first
        List<RelationEntry> cached = cache.get(tabPath);
        if (cached != null) {
            callback.onRelationsResolved(cached);
            return;
        }

        if (wikiService == null) {
            callback.onRelationsResolved(Collections.<RelationEntry>emptyList());
            return;
        }

        currentTask = pool.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    List<String> links = wikiService.loadOutgoingLinks(
                            siteId, pageTitle, WikiCredentials.anonymous());

                    if (Thread.currentThread().isInterrupted()) return;

                    final List<RelationEntry> entries = new ArrayList<RelationEntry>();
                    for (String link : links) {
                        String path = "wiki://" + siteId.value() + "/" + link;
                        entries.add(new RelationEntry(link, path, "WIKI_LINK"));
                    }

                    // Store in cache
                    cache.put(tabPath, entries);

                    // Deliver on EDT
                    javax.swing.SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            callback.onRelationsResolved(entries);
                        }
                    });
                } catch (Exception e) {
                    if (!Thread.currentThread().isInterrupted()) {
                        LOG.log(Level.FINE, "[RelationsService] Failed to resolve links for " + pageTitle, e);
                        javax.swing.SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                callback.onRelationsResolved(Collections.<RelationEntry>emptyList());
                            }
                        });
                    }
                }
            }
        });
    }

    public void cancelCurrent() {
        Future<?> task = currentTask;
        if (task != null) {
            task.cancel(true);
            currentTask = null;
        }
    }

    public void shutdown() {
        cancelCurrent();
        pool.shutdownNow();
        cache.clear();
    }

    public void clearCache() {
        cache.clear();
    }

    // ════════════════════════════════════════════════════════════
    //  Callback
    // ════════════════════════════════════════════════════════════

    public interface RelationsCallback {
        /** Called on the EDT with resolved relations. */
        void onRelationsResolved(List<RelationEntry> entries);
    }
}

