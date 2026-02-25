package de.bund.zrb.archive.service;

import de.bund.zrb.archive.model.ArchiveDocument;
import de.bund.zrb.archive.model.ArchiveResource;
import de.bund.zrb.archive.model.ResourceKind;
import de.bund.zrb.archive.store.ArchiveRepository;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Catalog pipeline: derives Documents from indexable Resources.
 * <ul>
 *   <li>Classifies resources by kind</li>
 *   <li>Extracts title, excerpt, and text content from HTML/text</li>
 *   <li>Creates ArchiveDocument entries in H2</li>
 *   <li>Stores extracted text for Lucene indexing</li>
 * </ul>
 */
public class CatalogPipeline {

    private static final Logger LOG = Logger.getLogger(CatalogPipeline.class.getName());

    private static final int MAX_EXCERPT_LENGTH = 1200;
    private static final int MAX_TITLE_LENGTH = 512;

    private static final Pattern TITLE_PATTERN = Pattern.compile(
            "<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern OG_TITLE_PATTERN = Pattern.compile(
            "<meta[^>]+property=[\"']og:title[\"'][^>]+content=[\"'](.*?)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern H1_PATTERN = Pattern.compile(
            "<h1[^>]*>(.*?)</h1>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private final ArchiveRepository repository;
    private final ResourceStorageService storageService;

    public CatalogPipeline(ArchiveRepository repository, ResourceStorageService storageService) {
        this.repository = repository;
        this.storageService = storageService;
    }

    /**
     * Process a resource and create a Document if the resource is indexable.
     *
     * @param resource    the resource to process
     * @param bodyText    the raw body text
     * @return the created ArchiveDocument, or null if not indexable
     */
    public ArchiveDocument process(ArchiveResource resource, String bodyText) {
        if (resource == null || bodyText == null || bodyText.isEmpty()) return null;
        if (!resource.isIndexable()) {
            LOG.fine("[CatalogPipeline] Skipping non-indexable resource: " + resource.getUrl());
            return null;
        }

        try {
            ResourceKind kind = ResourceKind.valueOf(resource.getKind());
            String title = extractTitle(bodyText, resource.getUrl(), kind);
            String textContent = extractTextContent(bodyText, kind);
            String excerpt = generateExcerpt(textContent);

            if (textContent.isEmpty() || textContent.length() < 50) {
                LOG.fine("[CatalogPipeline] Insufficient text for document: " + resource.getUrl());
                return null;
            }

            // Determine document kind
            ArchiveDocument.Kind docKind = determineDocKind(kind, textContent);

            // Store extracted text
            String textHash = ContentHasher.hash(textContent);
            String textPath = storageService.store(
                    resource.getRunId(),
                    UrlNormalizer.extractHost(resource.getUrl()),
                    ResourceKind.PAGE_TEXT,
                    "doc_" + textHash,
                    textContent
            );

            // Create ArchiveDocument
            ArchiveDocument doc = new ArchiveDocument();
            doc.setRunId(resource.getRunId());
            doc.setCreatedAt(System.currentTimeMillis());
            doc.setKind(docKind.name());
            doc.setTitle(title);
            doc.setCanonicalUrl(resource.getCanonicalUrl());
            doc.setSourceResourceIds(resource.getResourceId());
            doc.setExcerpt(excerpt);
            doc.setTextContentPath(textPath != null ? textPath : "");
            doc.setLanguage(detectLanguage(textContent));
            doc.setWordCount(countWords(textContent));
            doc.setHost(UrlNormalizer.extractHost(resource.getUrl()));

            repository.saveDocument(doc);

            LOG.fine("[CatalogPipeline] Document created: " + doc.getDocId() + " – " + title);
            return doc;

        } catch (Exception e) {
            LOG.log(Level.WARNING, "[CatalogPipeline] Failed to process resource: " + resource.getUrl(), e);
            return null;
        }
    }

    /**
     * Extract a stable title from HTML content.
     * Priority: OG:title > <title> > <h1> > host+path (never just URL).
     */
    private String extractTitle(String body, String url, ResourceKind kind) {
        if (kind == ResourceKind.PAGE_HTML || kind == ResourceKind.DOM_SNAPSHOT) {
            // Try OG:title first
            Matcher ogMatcher = OG_TITLE_PATTERN.matcher(body);
            if (ogMatcher.find()) {
                String ogTitle = cleanHtml(ogMatcher.group(1)).trim();
                if (!ogTitle.isEmpty() && ogTitle.length() < MAX_TITLE_LENGTH) {
                    return truncate(ogTitle, MAX_TITLE_LENGTH);
                }
            }

            // Try <title>
            Matcher titleMatcher = TITLE_PATTERN.matcher(body);
            if (titleMatcher.find()) {
                String htmlTitle = cleanHtml(titleMatcher.group(1)).trim();
                if (!htmlTitle.isEmpty() && htmlTitle.length() < MAX_TITLE_LENGTH) {
                    return truncate(htmlTitle, MAX_TITLE_LENGTH);
                }
            }

            // Try <h1>
            Matcher h1Matcher = H1_PATTERN.matcher(body);
            if (h1Matcher.find()) {
                String h1 = cleanHtml(h1Matcher.group(1)).trim();
                if (!h1.isEmpty() && h1.length() < MAX_TITLE_LENGTH) {
                    return truncate(h1, MAX_TITLE_LENGTH);
                }
            }
        }

        if (kind == ResourceKind.API_JSON) {
            // Try JSON title/headline/name fields
            String jsonTitle = extractJsonField(body, "title", "headline", "name");
            if (jsonTitle != null) {
                return truncate(jsonTitle, MAX_TITLE_LENGTH);
            }
        }

        // Fallback: host + path (never just raw URL)
        return truncate(buildTitleFromUrl(url), MAX_TITLE_LENGTH);
    }

    /**
     * Build a stable, readable title from a URL (host + path, no query).
     */
    private String buildTitleFromUrl(String url) {
        try {
            java.net.URI uri = new java.net.URI(url);
            String host = uri.getHost() != null ? uri.getHost() : "unknown";
            String path = uri.getPath();
            if (path == null || path.equals("/") || path.isEmpty()) {
                return host;
            }
            // Clean up path: remove trailing slash, replace / with " › "
            if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
            path = path.replace("/", " › ");
            String result = host + path;
            if (result.length() > 120) result = result.substring(0, 120) + "…";
            return result;
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * Extract plain text content from HTML by removing tags and normalizing whitespace.
     */
    private String extractTextContent(String body, ResourceKind kind) {
        if (kind == ResourceKind.PAGE_HTML || kind == ResourceKind.DOM_SNAPSHOT) {
            // Remove script and style blocks
            String cleaned = body.replaceAll("(?is)<script[^>]*>.*?</script>", " ");
            cleaned = cleaned.replaceAll("(?is)<style[^>]*>.*?</style>", " ");
            cleaned = cleaned.replaceAll("(?is)<nav[^>]*>.*?</nav>", " ");
            cleaned = cleaned.replaceAll("(?is)<footer[^>]*>.*?</footer>", " ");
            // Remove all remaining tags
            cleaned = HTML_TAG_PATTERN.matcher(cleaned).replaceAll(" ");
            // Decode common HTML entities
            cleaned = cleaned.replace("&nbsp;", " ")
                    .replace("&amp;", "&")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&quot;", "\"")
                    .replace("&#39;", "'");
            // Normalize whitespace
            cleaned = WHITESPACE_PATTERN.matcher(cleaned).replaceAll(" ").trim();
            return cleaned;
        }

        if (kind == ResourceKind.PAGE_TEXT || kind == ResourceKind.FEED_RSS || kind == ResourceKind.FEED_ATOM) {
            return body.trim();
        }

        if (kind == ResourceKind.API_JSON) {
            // Extract text-like fields from JSON
            return extractTextFromJson(body);
        }

        return body.trim();
    }

    /**
     * Generate a short excerpt from extracted text.
     */
    private String generateExcerpt(String text) {
        if (text == null || text.isEmpty()) return "";
        String excerpt = text.length() <= MAX_EXCERPT_LENGTH
                ? text
                : text.substring(0, MAX_EXCERPT_LENGTH);
        // Try to break at word boundary
        if (excerpt.length() == MAX_EXCERPT_LENGTH) {
            int lastSpace = excerpt.lastIndexOf(' ');
            if (lastSpace > MAX_EXCERPT_LENGTH * 0.7) {
                excerpt = excerpt.substring(0, lastSpace) + "…";
            } else {
                excerpt += "…";
            }
        }
        return excerpt;
    }

    private ArchiveDocument.Kind determineDocKind(ResourceKind resourceKind, String textContent) {
        switch (resourceKind) {
            case PAGE_HTML:
            case DOM_SNAPSHOT:
                if (textContent.length() > 2000) return ArchiveDocument.Kind.ARTICLE;
                return ArchiveDocument.Kind.PAGE;
            case FEED_RSS:
            case FEED_ATOM:
                return ArchiveDocument.Kind.FEED_ENTRY;
            case API_JSON:
                return ArchiveDocument.Kind.OTHER;
            default:
                return ArchiveDocument.Kind.PAGE;
        }
    }

    /**
     * Simple heuristic language detection based on common words.
     */
    private String detectLanguage(String text) {
        if (text == null || text.length() < 100) return "unknown";
        String lower = text.toLowerCase();
        // Count German indicators
        int deScore = 0;
        if (lower.contains(" und ")) deScore++;
        if (lower.contains(" der ")) deScore++;
        if (lower.contains(" die ")) deScore++;
        if (lower.contains(" das ")) deScore++;
        if (lower.contains(" ist ")) deScore++;
        if (lower.contains(" nicht ")) deScore++;

        // Count English indicators
        int enScore = 0;
        if (lower.contains(" the ")) enScore++;
        if (lower.contains(" and ")) enScore++;
        if (lower.contains(" is ")) enScore++;
        if (lower.contains(" are ")) enScore++;
        if (lower.contains(" not ")) enScore++;
        if (lower.contains(" this ")) enScore++;

        if (deScore > enScore) return "de";
        if (enScore > deScore) return "en";
        return "unknown";
    }

    private int countWords(String text) {
        if (text == null || text.isEmpty()) return 0;
        return text.split("\\s+").length;
    }

    private String cleanHtml(String text) {
        if (text == null) return "";
        return HTML_TAG_PATTERN.matcher(text).replaceAll("").trim();
    }

    private String truncate(String value, int maxLen) {
        if (value == null) return "";
        return value.length() <= maxLen ? value : value.substring(0, maxLen) + "…";
    }

    /**
     * Try to extract a named field from JSON text (simple regex-based, no JSON parser needed).
     */
    private String extractJsonField(String json, String... fieldNames) {
        for (String field : fieldNames) {
            Pattern p = Pattern.compile("\"" + field + "\"\\s*:\\s*\"(.*?)\"", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(json);
            if (m.find()) {
                String val = m.group(1).trim();
                if (!val.isEmpty() && val.length() < 500) return val;
            }
        }
        return null;
    }

    /**
     * Extract text-like content from JSON (simple heuristic: find string values > 50 chars).
     */
    private String extractTextFromJson(String json) {
        StringBuilder sb = new StringBuilder();
        Pattern p = Pattern.compile("\"(?:text|content|body|description|summary|abstract|article|message|snippet)\"\\s*:\\s*\"(.*?)\"",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m = p.matcher(json);
        while (m.find()) {
            String val = m.group(1).trim();
            if (val.length() > 50) {
                if (sb.length() > 0) sb.append("\n\n");
                sb.append(val);
            }
        }
        return sb.length() > 0 ? sb.toString() : json;
    }
}
