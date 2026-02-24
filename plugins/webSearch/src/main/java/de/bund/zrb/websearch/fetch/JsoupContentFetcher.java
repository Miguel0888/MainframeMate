package de.bund.zrb.websearch.fetch;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Content fetcher that uses Jsoup for direct HTTP-based page retrieval.
 * No browser needed – fast, lightweight, and reliable.
 *
 * <p>Advantages over browser-based fetching:</p>
 * <ul>
 *     <li>No browser process to manage (no timeouts, no hangs)</li>
 *     <li>Much faster (~1-3s vs 10-60s)</li>
 *     <li>No WebSocket connection issues</li>
 *     <li>Works for most content-oriented pages</li>
 * </ul>
 *
 * <p>Limitations:</p>
 * <ul>
 *     <li>Cannot execute JavaScript (SPAs, React apps)</li>
 *     <li>May be blocked by anti-bot protection (Cloudflare, etc.)</li>
 *     <li>Cannot interact with page elements</li>
 * </ul>
 */
public class JsoupContentFetcher implements ContentFetcher {

    private static final Logger LOG = Logger.getLogger(JsoupContentFetcher.class.getName());

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0";

    // Selectors for main content extraction, tried in order
    private static final String[] CONTENT_SELECTORS = {
            "article",
            "[role=main]",
            "main",
            "#article-container",
            ".article-body",
            ".caas-body",
            ".caas-content-wrapper",
            "#Main",
            "[data-content-area]",
            ".ntk-lead",
            "#content",
            ".content",
            ".post-content",
            ".entry-content",
            ".story-body",
            "#story-body",
            ".article__body"
    };

    // Elements to remove for cleaner text extraction
    private static final String NOISE_SELECTOR =
            "script, style, nav, footer, header, noscript, iframe, "
          + "[aria-hidden=true], .ad, .ads, .advertisement, .advert, "
          + ".social-share, .share-buttons, .related-articles, "
          + ".comments, #comments, .sidebar, aside, "
          + ".cookie-banner, .cookie-notice, .consent, "
          + ".newsletter, .popup, .modal, "
          + "[class*='promo'], [class*='banner'], [id*='banner']";

    @Override
    public String getName() {
        return "Jsoup";
    }

    @Override
    public FetchResult fetch(String url) {
        return fetch(url, FetchOptions.DEFAULT);
    }

    @Override
    public FetchResult fetch(String url, FetchOptions options) {
        LOG.info("[JsoupFetcher] Fetching: " + url);
        long start = System.currentTimeMillis();

        try {
            Connection.Response response = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(options.getTimeoutMs())
                    .maxBodySize(5 * 1024 * 1024) // 5 MB max
                    .followRedirects(true)
                    .ignoreHttpErrors(true)
                    .referrer("https://www.google.com/")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "de,en;q=0.9")
                    .header("Accept-Encoding", "gzip, deflate")
                    .header("DNT", "1")
                    .execute();

            int statusCode = response.statusCode();
            String finalUrl = response.url().toString();

            if (statusCode >= 400) {
                LOG.warning("[JsoupFetcher] HTTP " + statusCode + " for: " + url);
                return FetchResult.builder()
                        .success(false)
                        .source(FetchResult.Source.JSOUP)
                        .url(finalUrl)
                        .statusCode(statusCode)
                        .errorMessage("HTTP " + statusCode)
                        .build();
            }

            Document doc = response.parse();

            // Extract title
            String title = doc.title();

            // Extract meta description
            String metaDescription = extractMetaDescription(doc);

            // Remove noise elements
            doc.select(NOISE_SELECTOR).remove();

            // Extract main content text
            String text = extractMainText(doc, options.getCssSelector(), options.getMaxTextLength());

            // Extract links if requested
            List<FetchResult.Link> links = options.isIncludeLinks()
                    ? extractLinks(doc, finalUrl) : new ArrayList<FetchResult.Link>();

            long elapsed = System.currentTimeMillis() - start;
            LOG.info("[JsoupFetcher] Fetched in " + elapsed + "ms: " + finalUrl
                    + " (title=" + title + ", textLen=" + (text != null ? text.length() : 0)
                    + ", links=" + links.size() + ")");

            return FetchResult.builder()
                    .success(true)
                    .source(FetchResult.Source.JSOUP)
                    .url(finalUrl)
                    .title(title)
                    .text(text)
                    .metaDescription(metaDescription)
                    .links(links)
                    .statusCode(statusCode)
                    .build();

        } catch (IOException e) {
            long elapsed = System.currentTimeMillis() - start;
            LOG.log(Level.WARNING, "[JsoupFetcher] Failed after " + elapsed + "ms: " + url + " – " + e.getMessage(), e);
            return FetchResult.error(url, "Fetch failed: " + e.getMessage());
        }
    }

    /**
     * Extract the main text content of the page.
     */
    private String extractMainText(Document doc, String customSelector, int maxLength) {
        Element contentElement = null;

        // Try custom selector first
        if (customSelector != null && !customSelector.isEmpty()) {
            contentElement = doc.selectFirst(customSelector);
        }

        // Try known content selectors
        if (contentElement == null) {
            for (String selector : CONTENT_SELECTORS) {
                contentElement = doc.selectFirst(selector);
                if (contentElement != null) {
                    // Verify it has substantial text content
                    String candidateText = contentElement.text();
                    if (candidateText != null && candidateText.length() > 100) {
                        break;
                    }
                    contentElement = null; // too little content, try next
                }
            }
        }

        // Fall back to body
        if (contentElement == null) {
            contentElement = doc.body();
        }

        if (contentElement == null) {
            return "";
        }

        // Extract text with structure preservation
        String text = extractStructuredText(contentElement);

        // Clean up excessive whitespace
        text = text.replaceAll("\\t", " ")
                   .replaceAll(" {3,}", " ")
                   .replaceAll("\\n{3,}", "\n\n")
                   .trim();

        // Truncate if needed
        if (text.length() > maxLength) {
            text = text.substring(0, maxLength);
        }

        return text;
    }

    /**
     * Extract text preserving some structure (headings, paragraphs, list items).
     */
    private String extractStructuredText(Element root) {
        StringBuilder sb = new StringBuilder();

        for (Element child : root.children()) {
            String tag = child.tagName().toLowerCase();

            if ("h1".equals(tag) || "h2".equals(tag) || "h3".equals(tag)
                    || "h4".equals(tag) || "h5".equals(tag) || "h6".equals(tag)) {
                sb.append("\n\n## ").append(child.text().trim()).append("\n\n");
            } else if ("p".equals(tag)) {
                String pText = child.text().trim();
                if (!pText.isEmpty()) {
                    sb.append(pText).append("\n\n");
                }
            } else if ("li".equals(tag)) {
                sb.append("• ").append(child.text().trim()).append("\n");
            } else if ("ul".equals(tag) || "ol".equals(tag)) {
                sb.append(extractStructuredText(child));
                sb.append("\n");
            } else if ("br".equals(tag)) {
                sb.append("\n");
            } else if ("div".equals(tag) || "section".equals(tag) || "span".equals(tag)) {
                // Recurse into containers
                String inner = extractStructuredText(child);
                if (!inner.trim().isEmpty()) {
                    sb.append(inner);
                }
            } else if ("blockquote".equals(tag)) {
                sb.append("> ").append(child.text().trim()).append("\n\n");
            } else if ("pre".equals(tag) || "code".equals(tag)) {
                sb.append("```\n").append(child.text().trim()).append("\n```\n\n");
            } else if ("table".equals(tag)) {
                sb.append(extractTableText(child)).append("\n\n");
            } else {
                // For other elements, just get text if no children to recurse into
                if (child.children().isEmpty()) {
                    String text = child.text().trim();
                    if (!text.isEmpty()) {
                        sb.append(text).append(" ");
                    }
                } else {
                    sb.append(extractStructuredText(child));
                }
            }
        }

        // If no children produced output, use ownText
        if (sb.length() == 0) {
            String ownText = root.ownText().trim();
            if (!ownText.isEmpty()) {
                sb.append(ownText);
            }
        }

        return sb.toString();
    }

    /**
     * Extract text from a table element in a readable format.
     */
    private String extractTableText(Element table) {
        StringBuilder sb = new StringBuilder();
        Elements rows = table.select("tr");
        for (Element row : rows) {
            Elements cells = row.select("th, td");
            StringBuilder rowText = new StringBuilder();
            for (Element cell : cells) {
                if (rowText.length() > 0) rowText.append(" | ");
                rowText.append(cell.text().trim());
            }
            if (rowText.length() > 0) {
                sb.append(rowText).append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * Extract meta description from the page.
     */
    private String extractMetaDescription(Document doc) {
        Element meta = doc.selectFirst("meta[name=description]");
        if (meta != null) {
            String content = meta.attr("content");
            if (content != null && !content.isEmpty()) return content;
        }
        // Try og:description
        meta = doc.selectFirst("meta[property=og:description]");
        if (meta != null) {
            String content = meta.attr("content");
            if (content != null && !content.isEmpty()) return content;
        }
        return null;
    }

    /**
     * Extract links from the page, filtering out noise.
     */
    private List<FetchResult.Link> extractLinks(Document doc, String baseUrl) {
        List<FetchResult.Link> links = new ArrayList<>();
        Elements anchors = doc.select("a[href]");

        for (Element a : anchors) {
            String href = a.absUrl("href");
            if (href.isEmpty()) continue;

            // Skip non-HTTP links
            if (!href.startsWith("http://") && !href.startsWith("https://")) continue;
            // Skip javascript links
            if (href.startsWith("javascript:")) continue;
            // Skip fragment-only links to same page
            if (href.contains("#") && href.substring(0, href.indexOf('#')).equals(baseUrl)) continue;

            String text = a.text().trim();
            if (text.isEmpty()) text = a.attr("title");
            if (text.isEmpty()) text = a.attr("aria-label");
            if (text.isEmpty()) continue; // skip links without meaningful text

            // Skip very short or generic link text
            if (text.length() < 2) continue;

            links.add(new FetchResult.Link(href, text));

            // Limit to prevent excessive output
            if (links.size() >= 50) break;
        }

        return links;
    }
}
