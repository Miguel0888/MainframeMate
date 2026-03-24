package de.bund.zrb.search.provider;

import de.bund.zrb.archive.store.CacheRepository;
import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;
import de.bund.zrb.search.BackendSearchProvider;
import de.bund.zrb.search.SearchResult;
import de.bund.zrb.util.CredentialStore;
import de.bund.zrb.wiki.domain.WikiCredentials;
import de.bund.zrb.wiki.domain.WikiPageView;
import de.bund.zrb.wiki.domain.WikiSiteDescriptor;
import de.bund.zrb.wiki.domain.WikiSiteId;
import de.bund.zrb.wiki.infrastructure.JwbfWikiContentService;
import de.bund.zrb.wiki.service.WikiPrefetchService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Singleton live backend search provider for MediaWiki sites.
 * <p>
 * Reads configured wiki sites from {@code settings.passwordEntries}
 * (category "Wiki") on each search call — no constructor wiring needed.
 * Credentials are resolved on-demand from {@link CredentialStore}.
 * <p>
 * The underlying {@link JwbfWikiContentService} is cached and rebuilt
 * only when the configured site list changes.
 * <p>
 * Implements prefetch: after search, all result pages are preloaded in the
 * background using a {@link WikiPrefetchService} per site. When preview is
 * requested via {@link #loadContentHtml(String)}, it either returns the
 * cached content instantly or uses priority-load to fetch it synchronously.
 */
public final class WikiSearchProvider implements BackendSearchProvider {

    private static final Logger LOG = Logger.getLogger(WikiSearchProvider.class.getName());
    private static final WikiSearchProvider INSTANCE = new WikiSearchProvider();

    /** Cached service — rebuilt when the site fingerprint changes. */
    private volatile JwbfWikiContentService cachedService;
    /** Fingerprint of the site list used to build {@link #cachedService}. */
    private volatile String cachedFingerprint = "";

    /** Per-site prefetch services: siteId → WikiPrefetchService. */
    private final ConcurrentHashMap<String, WikiPrefetchService> prefetchServices =
            new ConcurrentHashMap<String, WikiPrefetchService>();

    private WikiSearchProvider() {}

    public static WikiSearchProvider getInstance() {
        return INSTANCE;
    }

    @Override
    public SearchResult.SourceType getSourceType() {
        return SearchResult.SourceType.WIKI;
    }

    @Override
    public boolean isAvailable() {
        return !loadSiteMetas(null).isEmpty();
    }

    @Override
    public List<SearchResult> search(String query, int maxResults, String networkZone) throws Exception {
        List<SiteMeta> metas = loadSiteMetas(networkZone);
        if (metas.isEmpty()) return Collections.emptyList();

        JwbfWikiContentService service = getOrCreateService();
        if (service == null) return Collections.emptyList();

        List<SearchResult> results = new ArrayList<SearchResult>();
        for (SiteMeta sm : metas) {
            if (results.size() >= maxResults) break;
            try {
                WikiCredentials creds = resolveCredentials(sm.id);
                WikiSiteId siteId = new WikiSiteId(sm.id);
                int limit = Math.min(maxResults - results.size(), 50);
                List<String> titles = service.searchPages(siteId, query, creds, limit);

                for (String title : titles) {
                    if (results.size() >= maxResults) break;
                    String docId = "wiki://" + sm.id + "/" + title;
                    results.add(new SearchResult(
                            SearchResult.SourceType.WIKI,
                            docId,
                            title,
                            sm.displayName,
                            "",   // MediaWiki search API returns titles only
                            0.8f, // slightly lower than Lucene-indexed results
                            docId,
                            sm.displayName
                    ));
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING, "[WikiSearch] Search failed for site " + sm.displayName, e);
                // Continue with other sites
            }
        }
        return results;
    }

    // ═══════════════════════════════════════════════════════════
    //  Prefetch / Content preloading
    // ═══════════════════════════════════════════════════════════

    @Override
    public void preloadResults(List<SearchResult> results) {
        if (results == null || results.isEmpty()) return;

        // Group results by siteId
        ConcurrentHashMap<String, List<String>> bySite =
                new ConcurrentHashMap<String, List<String>>();
        for (SearchResult r : results) {
            if (r.getSource() != SearchResult.SourceType.WIKI) continue;
            String docId = r.getDocumentId();
            if (docId == null || !docId.startsWith("wiki://")) continue;
            String rest = docId.substring("wiki://".length());
            int slash = rest.indexOf('/');
            if (slash <= 0) continue;
            String siteId = rest.substring(0, slash);
            String pageTitle = rest.substring(slash + 1);
            if (!bySite.containsKey(siteId)) {
                bySite.put(siteId, new ArrayList<String>());
            }
            bySite.get(siteId).add(pageTitle);
        }

        // Trigger prefetch for each site
        for (String siteId : bySite.keySet()) {
            WikiPrefetchService pfs = getOrCreatePrefetchService(siteId);
            if (pfs != null) {
                List<String> titles = bySite.get(siteId);
                pfs.prefetchSearchResults(new WikiSiteId(siteId), titles, 0);
            }
        }
    }

    @Override
    public String loadContentHtml(String docId) {
        if (docId == null || !docId.startsWith("wiki://")) return null;
        String rest = docId.substring("wiki://".length());
        int slash = rest.indexOf('/');
        if (slash <= 0) return null;
        String siteId = rest.substring(0, slash);
        String pageTitle = rest.substring(slash + 1);

        WikiPrefetchService pfs = getOrCreatePrefetchService(siteId);
        if (pfs == null) return null;

        WikiSiteId wikiSiteId = new WikiSiteId(siteId);

        // Try cache first (O(1))
        WikiPageView cached = pfs.getCached(wikiSiteId, pageTitle);
        if (cached != null) {
            return cached.htmlWithImages() != null ? cached.htmlWithImages() : cached.cleanedHtml();
        }

        // Priority load — dedicated thread, not blocked by prefetch queue
        WikiPageView loaded = pfs.loadPriority(wikiSiteId, pageTitle);
        if (loaded != null) {
            return loaded.htmlWithImages() != null ? loaded.htmlWithImages() : loaded.cleanedHtml();
        }
        return null;
    }

    @Override
    public void shutdown() {
        for (WikiPrefetchService pfs : prefetchServices.values()) {
            try { pfs.shutdown(); } catch (Exception ignored) {}
        }
        prefetchServices.clear();
    }

    // ── Internal helpers ──────────────────────────────────────────

    /**
     * Get or create a {@link WikiPrefetchService} for the given siteId.
     * The prefetch service shares the same {@link JwbfWikiContentService}.
     */
    private WikiPrefetchService getOrCreatePrefetchService(String siteId) {
        WikiPrefetchService existing = prefetchServices.get(siteId);
        if (existing != null) return existing;

        JwbfWikiContentService service = getOrCreateService();
        if (service == null) return null;

        CacheRepository cacheRepo = null;
        try {
            cacheRepo = CacheRepository.getInstance();
        } catch (Exception e) {
            LOG.log(Level.FINE, "[WikiSearch] CacheRepository not available", e);
        }

        WikiPrefetchService pfs = new WikiPrefetchService(service, cacheRepo, 50, 200, 4);
        // Wire credentials resolver
        pfs.setCredentialsResolver(new java.util.function.Function<WikiSiteId, WikiCredentials>() {
            @Override
            public WikiCredentials apply(WikiSiteId sid) {
                return resolveCredentials(sid.value());
            }
        });

        WikiPrefetchService prev = prefetchServices.putIfAbsent(siteId, pfs);
        return prev != null ? prev : pfs;
    }

    /**
     * Get or create the shared {@link JwbfWikiContentService}.
     * Rebuilds the service when the configured site list changes.
     */
    private JwbfWikiContentService getOrCreateService() {
        List<SiteMeta> allMetas = loadSiteMetas(null); // all zones
        String fp = buildFingerprint(allMetas);
        if (cachedService != null && fp.equals(cachedFingerprint)) {
            return cachedService;
        }
        synchronized (this) {
            if (cachedService != null && fp.equals(cachedFingerprint)) {
                return cachedService;
            }
            if (allMetas.isEmpty()) {
                cachedService = null;
                cachedFingerprint = fp;
                return null;
            }
            // Shutdown old prefetch services when service is rebuilt
            for (WikiPrefetchService pfs : prefetchServices.values()) {
                try { pfs.shutdown(); } catch (Exception ignored) {}
            }
            prefetchServices.clear();

            List<WikiSiteDescriptor> descriptors = new ArrayList<WikiSiteDescriptor>();
            for (SiteMeta sm : allMetas) {
                descriptors.add(new WikiSiteDescriptor(
                        new WikiSiteId(sm.id), sm.displayName, sm.url,
                        sm.requiresLogin, sm.useProxy, sm.autoIndex));
            }
            JwbfWikiContentService svc = new JwbfWikiContentService(descriptors);
            // Wire proxy resolver
            svc.setProxyResolver(url -> {
                Settings s = SettingsHelper.load();
                de.bund.zrb.net.ProxyResolver.ProxyResolution res =
                        de.bund.zrb.net.ProxyResolver.resolveForUrl(url, s);
                return res.getProxy();
            });
            // Also wire proxy for async image downloads (upload.wikimedia.org etc.)
            java.util.function.Function<String, java.net.Proxy> imgProxy = url -> {
                Settings s = SettingsHelper.load();
                de.bund.zrb.net.ProxyResolver.ProxyResolution res =
                        de.bund.zrb.net.ProxyResolver.resolveForUrl(url, s);
                return res.getProxy();
            };
            de.bund.zrb.wiki.ui.WikiAsyncImageLoader.setProxyResolver(imgProxy);
            de.bund.zrb.wiki.ui.ImageStripPanel.setProxyResolver(imgProxy);
            cachedService = svc;
            cachedFingerprint = fp;
            LOG.info("[WikiSearch] Rebuilt service with " + descriptors.size() + " site(s)");
            return svc;
        }
    }

    /** Load wiki site metadata from settings, optionally filtered by networkZone. */
    private static List<SiteMeta> loadSiteMetas(String networkZone) {
        Settings settings = SettingsHelper.load();
        List<SiteMeta> result = new ArrayList<SiteMeta>();
        for (Settings.PasswordEntryMeta meta : settings.passwordEntries) {
            if (!"Wiki".equals(meta.category)) continue;
            if (meta.url == null || meta.url.isEmpty()) continue;
            if (networkZone != null && !networkZone.equals(meta.networkZone)) continue;
            String name = (meta.displayName != null && !meta.displayName.isEmpty())
                    ? meta.displayName : meta.id;
            result.add(new SiteMeta(meta.id, name, meta.url,
                    meta.requiresLogin, meta.useProxy, meta.autoIndex));
        }
        return result;
    }

    /** Resolve credentials for a wiki site from the central credential store. */
    private static WikiCredentials resolveCredentials(String siteId) {
        try {
            String[] cred = CredentialStore.resolveIncludingEmpty("pwd:" + siteId);
            if (cred != null && !cred[0].isEmpty() && !cred[1].isEmpty()) {
                return new WikiCredentials(cred[0], cred[1].toCharArray());
            }
        } catch (Exception e) {
            // decryption failed or blocked — fall through to anonymous
        }
        return WikiCredentials.anonymous();
    }

    private static String buildFingerprint(List<SiteMeta> metas) {
        StringBuilder sb = new StringBuilder();
        for (SiteMeta sm : metas) {
            sb.append(sm.id).append('|').append(sm.url).append(';');
        }
        return sb.toString();
    }

    /** Lightweight holder for wiki site metadata from settings. */
    private static final class SiteMeta {
        final String id;
        final String displayName;
        final String url;
        final boolean requiresLogin;
        final boolean useProxy;
        final boolean autoIndex;

        SiteMeta(String id, String displayName, String url,
                 boolean requiresLogin, boolean useProxy, boolean autoIndex) {
            this.id = id;
            this.displayName = displayName;
            this.url = url;
            this.requiresLogin = requiresLogin;
            this.useProxy = useProxy;
            this.autoIndex = autoIndex;
        }
    }
}
