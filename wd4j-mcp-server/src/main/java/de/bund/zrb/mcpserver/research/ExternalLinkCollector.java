package de.bund.zrb.mcpserver.research;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Collects external links discovered during research, grouped by the source page URL.
 * Thread-safe for concurrent access from background crawl workers.
 * <p>
 * Usage pattern: after visiting a page, extract links, classify them, and add
 * all external links via {@link #addLinks(String, List)}.
 */
public class ExternalLinkCollector {

    /**
     * A single external link with its label and the source page URL.
     */
    public static class ExternalLink {
        public final String url;
        public final String label;
        public final String domain;

        public ExternalLink(String url, String label) {
            this.url = url;
            this.label = label != null ? label : "";
            this.domain = UrlCanonicalizer.extractDomain(url);
        }
    }

    /** sourceUrl → list of external links found on that page */
    private final Map<String, List<ExternalLink>> linksBySource = new ConcurrentHashMap<>();

    /** Dedup: all canonical external URLs already collected */
    private final Set<String> seenUrls = ConcurrentHashMap.newKeySet();

    /**
     * Add external links found on a source page. Duplicate URLs are skipped.
     *
     * @param sourceUrl    the internal page where these links were found
     * @param externalLinks the external links to add
     */
    public void addLinks(String sourceUrl, List<ExternalLink> externalLinks) {
        if (sourceUrl == null || externalLinks == null || externalLinks.isEmpty()) return;
        String canonSource = UrlCanonicalizer.canonicalize(sourceUrl);
        List<ExternalLink> bucket = linksBySource.computeIfAbsent(canonSource, k -> Collections.synchronizedList(new ArrayList<>()));
        for (ExternalLink link : externalLinks) {
            String canonLink = UrlCanonicalizer.canonicalize(link.url);
            if (seenUrls.add(canonLink)) {
                bucket.add(link);
            }
        }
    }

    /**
     * Returns all external links grouped by source page.
     */
    public Map<String, List<ExternalLink>> getAll() {
        return Collections.unmodifiableMap(linksBySource);
    }

    /**
     * Returns all external links matching a given domain (e.g. "reuters.com").
     * Searches across all source pages.
     */
    public List<ExternalLink> getByDomain(String domain) {
        if (domain == null) return Collections.emptyList();
        String lowerDomain = domain.toLowerCase();
        List<ExternalLink> result = new ArrayList<>();
        for (List<ExternalLink> bucket : linksBySource.values()) {
            synchronized (bucket) {
                for (ExternalLink link : bucket) {
                    if (link.domain != null && link.domain.contains(lowerDomain)) {
                        result.add(link);
                    }
                }
            }
        }
        return result;
    }

    /**
     * Returns a set of all distinct external domains collected so far.
     */
    public Set<String> getAllDomains() {
        Set<String> domains = new TreeSet<>();
        for (List<ExternalLink> bucket : linksBySource.values()) {
            synchronized (bucket) {
                for (ExternalLink link : bucket) {
                    if (link.domain != null) domains.add(link.domain);
                }
            }
        }
        return domains;
    }

    /**
     * Total count of unique external URLs collected.
     */
    public int getTotalCount() {
        return seenUrls.size();
    }

    /**
     * Clear all collected links (e.g. on session reset).
     */
    public void clear() {
        linksBySource.clear();
        seenUrls.clear();
    }
}
