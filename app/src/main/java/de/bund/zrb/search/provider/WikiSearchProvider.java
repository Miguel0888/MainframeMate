package de.bund.zrb.search.provider;

import de.bund.zrb.helper.SettingsHelper;
import de.bund.zrb.model.Settings;
import de.bund.zrb.search.BackendSearchProvider;
import de.bund.zrb.search.SearchResult;
import de.bund.zrb.util.CredentialStore;
import de.bund.zrb.wiki.domain.WikiCredentials;
import de.bund.zrb.wiki.domain.WikiSiteDescriptor;
import de.bund.zrb.wiki.domain.WikiSiteId;
import de.bund.zrb.wiki.infrastructure.JwbfWikiContentService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
 */
public final class WikiSearchProvider implements BackendSearchProvider {

    private static final Logger LOG = Logger.getLogger(WikiSearchProvider.class.getName());
    private static final WikiSearchProvider INSTANCE = new WikiSearchProvider();

    /** Cached service — rebuilt when the site fingerprint changes. */
    private volatile JwbfWikiContentService cachedService;
    /** Fingerprint of the site list used to build {@link #cachedService}. */
    private volatile String cachedFingerprint = "";

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

    // ── Internal helpers ──────────────────────────────────────────

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
