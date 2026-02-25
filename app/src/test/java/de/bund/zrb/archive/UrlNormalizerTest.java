package de.bund.zrb.archive;

import de.bund.zrb.archive.service.ContentHasher;
import de.bund.zrb.archive.service.UrlNormalizer;
import de.bund.zrb.archive.model.ResourceKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for URL normalization, content hashing, and resource classification.
 */
class UrlNormalizerTest {

    // ── URL Normalization ──

    @Test
    void canonicalize_removesFragment() {
        assertEquals("https://example.com/page",
                UrlNormalizer.canonicalize("https://example.com/page#section1"));
    }

    @Test
    void canonicalize_removesUtmParams() {
        assertEquals("https://example.com/article",
                UrlNormalizer.canonicalize("https://example.com/article?utm_source=twitter&utm_medium=social"));
    }

    @Test
    void canonicalize_removesSessionParams() {
        assertEquals("https://example.com/page",
                UrlNormalizer.canonicalize("https://example.com/page?jsessionid=abc123&fbclid=xyz"));
    }

    @Test
    void canonicalize_sortsQueryParams() {
        String result = UrlNormalizer.canonicalize("https://example.com/search?z=1&a=2&m=3");
        assertEquals("https://example.com/search?a=2&m=3&z=1", result);
    }

    @Test
    void canonicalize_lowercasesSchemeAndHost() {
        assertEquals("https://example.com/Path",
                UrlNormalizer.canonicalize("HTTPS://EXAMPLE.COM/Path"));
    }

    @Test
    void canonicalize_removesDefaultPort() {
        assertEquals("https://example.com/page",
                UrlNormalizer.canonicalize("https://example.com:443/page"));
        assertEquals("http://example.com/page",
                UrlNormalizer.canonicalize("http://example.com:80/page"));
    }

    @Test
    void canonicalize_removesTrailingSlash() {
        assertEquals("https://example.com/page",
                UrlNormalizer.canonicalize("https://example.com/page/"));
    }

    @Test
    void canonicalize_keepsMixedParams() {
        String result = UrlNormalizer.canonicalize("https://yahoo.com/news?article=123&utm_source=x&page=2");
        assertEquals("https://yahoo.com/news?article=123&page=2", result);
    }

    // ── Host Extraction ──

    @Test
    void extractHost_works() {
        assertEquals("www.yahoo.com", UrlNormalizer.extractHost("https://www.yahoo.com/news/article"));
        assertEquals("example.com", UrlNormalizer.extractHost("http://example.com"));
    }

    // ── Content Hashing ──

    @Test
    void hash_producesConsistentResult() {
        String hash1 = ContentHasher.hash("Hello, World!");
        String hash2 = ContentHasher.hash("Hello, World!");
        assertEquals(hash1, hash2);
        assertEquals(64, hash1.length()); // SHA-256 hex = 64 chars
    }

    @Test
    void hash_differsByContent() {
        assertNotEquals(
                ContentHasher.hash("Content A"),
                ContentHasher.hash("Content B"));
    }

    // ── Resource Classification ──

    @Test
    void resourceKind_classifiesHtml() {
        assertEquals(ResourceKind.PAGE_HTML,
                ResourceKind.fromMimeAndUrl("text/html", "https://example.com/page"));
    }

    @Test
    void resourceKind_classifiesJson() {
        assertEquals(ResourceKind.API_JSON,
                ResourceKind.fromMimeAndUrl("application/json", "https://api.example.com/data"));
    }

    @Test
    void resourceKind_classifiesTracking() {
        assertEquals(ResourceKind.TRACKING,
                ResourceKind.fromMimeAndUrl("text/html", "https://googlesyndication.com/pagead"));
    }

    @Test
    void resourceKind_classifiesJs() {
        assertEquals(ResourceKind.ASSET_JS,
                ResourceKind.fromMimeAndUrl("application/javascript", "https://cdn.example.com/app.js"));
    }

    @Test
    void resourceKind_defaultIndexable() {
        assertTrue(ResourceKind.PAGE_HTML.isDefaultIndexable());
        assertTrue(ResourceKind.PAGE_TEXT.isDefaultIndexable());
        assertTrue(ResourceKind.FEED_RSS.isDefaultIndexable());
        assertFalse(ResourceKind.TRACKING.isDefaultIndexable());
        assertFalse(ResourceKind.ASSET_JS.isDefaultIndexable());
        assertFalse(ResourceKind.ASSET_CSS.isDefaultIndexable());
        assertFalse(ResourceKind.IMAGE.isDefaultIndexable());
    }
}
