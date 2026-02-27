package de.bund.zrb.mcpserver.research;

import de.bund.zrb.command.response.WDBrowsingContextResult;
import de.bund.zrb.mcpserver.browser.BrowserSession;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Worker pool for background tab-based crawling within the same BrowserSession.
 * <p>
 * Each URL is opened in a new browser tab (browsing context), snapshot is taken,
 * the snapshot is archived via {@link SnapshotArchivingCallback}, links are extracted,
 * and the tab is closed – all without blocking the bot's interactive navigation.
 * <p>
 * The pool shares the browser session (and thus cookies/auth) with the interactive tab.
 * Concurrency is limited to {@code maxParallelTabs} to avoid tab explosion.
 */
public class BackgroundCrawlWorkerPool {

    private static final Logger LOG = Logger.getLogger(BackgroundCrawlWorkerPool.class.getName());

    /**
     * Result of a background crawl of a single URL.
     */
    public static class CrawlResult {
        public final String url;
        public final String title;
        public final List<String> internalLinks;
        public final List<ExternalLinkCollector.ExternalLink> externalLinks;
        public final String contentHash;
        public final boolean success;
        public final String error;

        CrawlResult(String url, String title, List<String> internalLinks,
                     List<ExternalLinkCollector.ExternalLink> externalLinks,
                     String contentHash, boolean success, String error) {
            this.url = url;
            this.title = title;
            this.internalLinks = internalLinks != null ? internalLinks : Collections.<String>emptyList();
            this.externalLinks = externalLinks != null ? externalLinks : Collections.<ExternalLinkCollector.ExternalLink>emptyList();
            this.contentHash = contentHash;
            this.success = success;
            this.error = error;
        }

        static CrawlResult success(String url, String title, List<String> internal,
                                   List<ExternalLinkCollector.ExternalLink> external, String hash) {
            return new CrawlResult(url, title, internal, external, hash, true, null);
        }

        static CrawlResult failure(String url, String error) {
            return new CrawlResult(url, null, null, null, null, false, error);
        }
    }

    private final ExecutorService executor;
    private final Semaphore tabSemaphore;
    private final AtomicInteger activeCount = new AtomicInteger(0);
    private final AtomicInteger pendingCount = new AtomicInteger(0);
    private final AtomicInteger completedCount = new AtomicInteger(0);
    private final List<Future<CrawlResult>> futures = new CopyOnWriteArrayList<>();
    private volatile boolean shutdown = false;

    public BackgroundCrawlWorkerPool(int maxParallelTabs) {
        this.executor = Executors.newFixedThreadPool(maxParallelTabs,
                r -> { Thread t = new Thread(r, "bg-crawl"); t.setDaemon(true); return t; });
        this.tabSemaphore = new Semaphore(maxParallelTabs);
    }

    /**
     * Submit a URL for background crawling.
     * The URL will be opened in a new tab, snapshotted, archived, and the tab closed.
     *
     * @param url       the URL to crawl
     * @param session   shared browser session
     * @param rs        research session (for archiving callback, link classifier, visited tracker)
     * @return Future with the crawl result
     */
    public Future<CrawlResult> submitCrawl(String url, BrowserSession session, ResearchSession rs) {
        if (shutdown) {
            return CompletableFuture.completedFuture(CrawlResult.failure(url, "Pool is shut down"));
        }
        pendingCount.incrementAndGet();
        Future<CrawlResult> future = executor.submit(() -> crawlInTab(url, session, rs));
        futures.add(future);
        return future;
    }

    private CrawlResult crawlInTab(String url, BrowserSession session, ResearchSession rs) {
        String tabCtxId = null;
        try {
            // Acquire semaphore slot (blocks if max tabs reached)
            tabSemaphore.acquire();
            pendingCount.decrementAndGet();
            activeCount.incrementAndGet();

            LOG.info("[BackgroundCrawl] Opening tab for: " + url);

            // 1. Create new tab
            synchronized (session) {
                WDBrowsingContextResult.CreateResult ctx = session.getDriver().browsingContext().create();
                tabCtxId = ctx.getContext();
            }

            // 2. Navigate in the new tab
            synchronized (session) {
                session.getDriver().browsingContext().navigate(
                        url, tabCtxId, de.bund.zrb.type.browsingContext.WDReadinessState.NONE);
            }

            // 3. Wait for settle
            long settleMs = Long.getLong("websearch.crawl.settleMs", 2000);
            Thread.sleep(settleMs);

            // 4. Fetch DOM snapshot from the tab
            String html = DomSnapshotFetcher.fetchHtml(session, 0, tabCtxId);

            if (html == null || html.isEmpty()) {
                return CrawlResult.failure(url, "Empty DOM snapshot");
            }

            // 5. Compute content hash (SHA-256 of innerText approximation)
            String contentHash = computeHash(html);

            // 6. Extract title
            String title = extractTitle(html);

            // 7. Archive snapshot
            archiveSnapshot(rs, url, html);

            // 8. Mark as visited
            rs.getVisitedUrls().markVisited(url);

            // 9. Extract and classify links
            List<String> internalLinks = new ArrayList<>();
            List<ExternalLinkCollector.ExternalLink> externalLinkList = new ArrayList<>();

            LinkClassifier classifier = rs.getLinkClassifier();
            if (classifier != null) {
                List<String[]> rawLinks = extractLinks(html, url);
                for (String[] link : rawLinks) {
                    String linkUrl = link[0];
                    String linkLabel = link[1];
                    if (classifier.isInternal(linkUrl)) {
                        if (!rs.getVisitedUrls().isVisited(linkUrl)) {
                            internalLinks.add(linkUrl);
                        }
                    } else {
                        externalLinkList.add(new ExternalLinkCollector.ExternalLink(linkUrl, linkLabel));
                    }
                }
                // Store external links
                rs.getExternalLinks().addLinks(url, externalLinkList);
            }

            LOG.info("[BackgroundCrawl] ✅ " + url + " (internal=" + internalLinks.size()
                    + ", external=" + externalLinkList.size() + ", hash=" + contentHash.substring(0, 8) + ")");

            return CrawlResult.success(url, title, internalLinks, externalLinkList, contentHash);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return CrawlResult.failure(url, "Interrupted");
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[BackgroundCrawl] Failed: " + url, e);
            return CrawlResult.failure(url, e.getMessage());
        } finally {
            // 10. Close tab (MUST happen after archive success)
            if (tabCtxId != null) {
                try {
                    synchronized (session) {
                        session.getDriver().browsingContext().close(tabCtxId);
                    }
                } catch (Exception e) {
                    LOG.fine("[BackgroundCrawl] Tab close failed for " + tabCtxId + ": " + e.getMessage());
                }
            }
            activeCount.decrementAndGet();
            completedCount.incrementAndGet();
            tabSemaphore.release();
        }
    }

    private void archiveSnapshot(ResearchSession rs, String url, String html) {
        SnapshotArchivingCallback callback = ResearchSessionManager.getSnapshotArchivingCallback();
        if (callback == null) return;
        try {
            String runId = rs.getRunId();
            String docId = callback.onSnapshotCaptured(runId, url, "text/html", 200, html, System.currentTimeMillis());
            if (docId != null) {
                rs.addArchivedDocId(docId);
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[BackgroundCrawl] Archive failed for " + url, e);
        }
    }

    /**
     * Extract links from HTML (href attributes from &lt;a&gt; tags).
     * Returns list of [absoluteUrl, label] pairs.
     */
    private List<String[]> extractLinks(String html, String baseUrl) {
        List<String[]> links = new ArrayList<>();
        if (html == null) return links;

        // Simple regex-based extraction (good enough for background crawling)
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "<a\\s[^>]*href\\s*=\\s*[\"']([^\"'#][^\"']*)[\"'][^>]*>(.*?)</a>",
                java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher matcher = pattern.matcher(html);

        Set<String> seen = new HashSet<>();
        while (matcher.find()) {
            String href = matcher.group(1).trim();
            String label = matcher.group(2).replaceAll("<[^>]+>", "").trim();
            if (label.length() > 80) label = label.substring(0, 80);

            // Resolve relative URLs
            String absoluteUrl = resolveUrl(href, baseUrl);
            if (absoluteUrl == null) continue;
            // Skip non-http URLs
            if (!absoluteUrl.startsWith("http://") && !absoluteUrl.startsWith("https://")) continue;

            String canon = UrlCanonicalizer.canonicalize(absoluteUrl);
            if (seen.add(canon)) {
                links.add(new String[]{absoluteUrl, label});
            }
        }
        return links;
    }

    private String resolveUrl(String href, String baseUrl) {
        if (href.startsWith("http://") || href.startsWith("https://")) return href;
        try {
            java.net.URI base = new java.net.URI(baseUrl);
            if (href.startsWith("/")) {
                return base.getScheme() + "://" + base.getAuthority() + href;
            }
            return base.resolve(href).toString();
        } catch (Exception e) {
            return null;
        }
    }

    private String extractTitle(String html) {
        if (html == null) return null;
        int start = html.indexOf("<title");
        if (start < 0) return null;
        int tagEnd = html.indexOf('>', start);
        if (tagEnd < 0) return null;
        int end = html.indexOf("</title>", tagEnd);
        if (end < 0) return null;
        return html.substring(tagEnd + 1, end).trim();
    }

    /**
     * SHA-256 hash of the HTML content for change detection.
     */
    static String computeHash(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    // ── Status ──

    public int getActiveCount() { return activeCount.get(); }
    public int getPendingCount() { return pendingCount.get(); }
    public int getCompletedCount() { return completedCount.get(); }

    /**
     * Shut down the worker pool. Waits up to 10s for active crawls to finish.
     */
    public void shutdown() {
        shutdown = true;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
