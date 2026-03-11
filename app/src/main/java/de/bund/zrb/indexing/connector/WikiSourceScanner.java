package de.bund.zrb.indexing.connector;

import de.bund.zrb.indexing.model.IndexSource;
import de.bund.zrb.indexing.model.ScannedItem;
import de.bund.zrb.indexing.port.SourceScanner;
import de.bund.zrb.wiki.domain.WikiCredentials;
import de.bund.zrb.wiki.domain.WikiSiteId;
import de.bund.zrb.wiki.port.WikiContentService;
import de.bund.zrb.wiki.domain.WikiPageView;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SourceScanner for MediaWiki sites.
 *
 * Uses {@link WikiContentService} to discover and fetch wiki pages.
 * Pages are discovered starting from the configured scope paths (page titles)
 * and optionally crawling outgoing links up to {@code maxCrawlDepth}.
 *
 * The fetched HTML is converted to plain text for Tika/RAG indexing.
 */
public class WikiSourceScanner implements SourceScanner {

    private static final Logger LOG = Logger.getLogger(WikiSourceScanner.class.getName());

    private volatile WikiContentService wikiService;
    private volatile java.util.function.Function<WikiSiteId, WikiCredentials> credentialsResolver;

    public void setWikiService(WikiContentService wikiService) {
        this.wikiService = wikiService;
    }

    public void setCredentialsResolver(java.util.function.Function<WikiSiteId, WikiCredentials> resolver) {
        this.credentialsResolver = resolver;
    }

    @Override
    public List<ScannedItem> scan(IndexSource source) throws Exception {
        if (wikiService == null) {
            throw new IllegalStateException("WikiContentService not set on WikiSourceScanner");
        }

        WikiSiteId siteId = new WikiSiteId(source.getConnectionHost());
        WikiCredentials creds = resolveCredentials(siteId);

        List<String> startPages = source.getScopePaths();
        if (startPages == null || startPages.isEmpty()) {
            LOG.info("[WikiIndexing] No start pages configured for: " + source.getName());
            return Collections.emptyList();
        }

        int maxDepth = source.getMaxCrawlDepth();
        int maxUrls = source.getMaxUrlsPerSession();

        Set<String> visited = new LinkedHashSet<>();
        List<String> currentLevel = new ArrayList<>(startPages);

        // BFS crawl
        for (int depth = 0; depth <= maxDepth && !currentLevel.isEmpty(); depth++) {
            List<String> nextLevel = new ArrayList<>();

            for (String pageTitle : currentLevel) {
                if (visited.contains(pageTitle)) continue;
                if (visited.size() >= maxUrls) break;
                visited.add(pageTitle);

                // Discover outgoing links for next level
                if (depth < maxDepth) {
                    try {
                        List<String> links = wikiService.loadOutgoingLinks(siteId, pageTitle, creds);
                        for (String link : links) {
                            if (!visited.contains(link)) {
                                nextLevel.add(link);
                            }
                        }
                    } catch (Exception e) {
                        LOG.log(Level.WARNING, "[WikiIndexing] Failed to load links for: " + pageTitle, e);
                    }
                }
            }

            if (visited.size() >= maxUrls) break;
            currentLevel = nextLevel;
        }

        LOG.info("[WikiIndexing] Discovered " + visited.size() + " pages (depth=" + maxDepth
                + ", max=" + maxUrls + ") for: " + source.getName());

        // Convert to ScannedItems
        List<ScannedItem> items = new ArrayList<>();
        for (String pageTitle : visited) {
            String path = "wiki://" + siteId.value() + "/" + pageTitle;
            items.add(new ScannedItem(path, System.currentTimeMillis(), 0, false, "text/html"));
        }
        return items;
    }

    @Override
    public byte[] fetchContent(IndexSource source, String itemPath) throws Exception {
        if (wikiService == null) {
            throw new IllegalStateException("WikiContentService not set");
        }

        // Parse "wiki://siteId/pageTitle"
        String remainder = itemPath.substring("wiki://".length());
        int slashIdx = remainder.indexOf('/');
        if (slashIdx < 0) {
            throw new IllegalArgumentException("Invalid wiki path: " + itemPath);
        }
        String siteIdStr = remainder.substring(0, slashIdx);
        String pageTitle = remainder.substring(slashIdx + 1);

        WikiSiteId siteId = new WikiSiteId(siteIdStr);
        WikiCredentials creds = resolveCredentials(siteId);

        WikiPageView page = wikiService.loadPage(siteId, pageTitle, creds);
        // Strip HTML tags for plain text indexing
        String text = stripHtml(page.cleanedHtml());
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private WikiCredentials resolveCredentials(WikiSiteId siteId) {
        if (credentialsResolver != null) {
            WikiCredentials creds = credentialsResolver.apply(siteId);
            if (creds != null) return creds;
        }
        return WikiCredentials.anonymous();
    }

    /**
     * Simple HTML→text conversion: strip tags, decode entities, collapse whitespace.
     */
    private static String stripHtml(String html) {
        if (html == null || html.isEmpty()) return "";
        // Remove script/style content
        String text = html.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", "");
        // Replace block elements with newlines
        text = text.replaceAll("(?i)<(br|p|div|h[1-6]|li|tr)[^>]*>", "\n");
        // Remove remaining tags
        text = text.replaceAll("<[^>]+>", "");
        // Decode common entities
        text = text.replace("&amp;", "&")
                   .replace("&lt;", "<")
                   .replace("&gt;", ">")
                   .replace("&quot;", "\"")
                   .replace("&nbsp;", " ")
                   .replace("&#39;", "'");
        // Collapse whitespace
        text = text.replaceAll("[ \\t]+", " ");
        text = text.replaceAll("\\n{3,}", "\n\n");
        return text.trim();
    }
}

