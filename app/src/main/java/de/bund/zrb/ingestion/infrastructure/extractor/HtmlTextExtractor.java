package de.bund.zrb.ingestion.infrastructure.extractor;

import de.bund.zrb.ingestion.model.DetectionResult;
import de.bund.zrb.ingestion.model.DocumentSource;
import de.bund.zrb.ingestion.model.ExtractionResult;
import de.bund.zrb.ingestion.port.TextExtractor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.jsoup.select.Elements;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Extractor for HTML files using jsoup.
 * Extracts readable text content, removing scripts, styles, and tags.
 */
public class HtmlTextExtractor implements TextExtractor {

    private static final List<String> SUPPORTED_MIME_TYPES = Arrays.asList(
            "text/html",
            "application/xhtml+xml"
    );

    @Override
    public boolean supports(String mimeType) {
        if (mimeType == null) return false;
        String baseMime = mimeType.contains(";") ? mimeType.substring(0, mimeType.indexOf(';')).trim() : mimeType;
        return SUPPORTED_MIME_TYPES.contains(baseMime);
    }

    @Override
    public int getPriority() {
        return 100;
    }

    @Override
    public String getName() {
        return "HtmlTextExtractor";
    }

    @Override
    public ExtractionResult extract(DocumentSource source, DetectionResult detection) {
        List<String> warnings = new ArrayList<>();
        Map<String, String> metadata = new HashMap<>();

        try {
            byte[] bytes = source.getBytes();
            if (bytes == null || bytes.length == 0) {
                return ExtractionResult.failure("Datei ist leer", getName());
            }

            // Determine charset
            Charset charset = determineCharset(detection);
            String html = new String(bytes, charset);

            // Parse HTML
            Document doc = Jsoup.parse(html);

            // Extract metadata
            String title = doc.title();
            if (title != null && !title.isEmpty()) {
                metadata.put("title", title);
            }

            // Extract meta description
            Element metaDesc = doc.selectFirst("meta[name=description]");
            if (metaDesc != null) {
                String content = metaDesc.attr("content");
                if (content != null && !content.isEmpty()) {
                    metadata.put("description", content);
                }
            }

            // Remove unwanted elements
            doc.select("script, style, noscript, iframe, object, embed").remove();
            doc.select("[style]").removeAttr("style");
            doc.select("[onclick], [onload], [onerror]").forEach(el -> {
                el.removeAttr("onclick");
                el.removeAttr("onload");
                el.removeAttr("onerror");
            });

            // Extract text content
            String text = extractTextContent(doc);

            return ExtractionResult.success(text, warnings, metadata, getName());

        } catch (Exception e) {
            return ExtractionResult.failure("Fehler beim Parsen der HTML-Datei: " + e.getMessage(), getName());
        }
    }

    private Charset determineCharset(DetectionResult detection) {
        if (detection != null && detection.getCharset() != null) {
            try {
                return Charset.forName(detection.getCharset());
            } catch (Exception e) {
                // Fall through to default
            }
        }
        return StandardCharsets.UTF_8;
    }

    private String extractTextContent(Document doc) {
        StringBuilder sb = new StringBuilder();

        // Add title if present
        String title = doc.title();
        if (title != null && !title.trim().isEmpty()) {
            sb.append(title.trim()).append("\n\n");
        }

        // Extract body content with structure preservation
        Element body = doc.body();
        if (body != null) {
            extractElementText(body, sb);
        }

        return sb.toString().trim();
    }

    private void extractElementText(Element element, StringBuilder sb) {
        String tagName = element.tagName().toLowerCase();

        // Handle block-level elements
        boolean isBlock = isBlockElement(tagName);
        boolean isListItem = "li".equals(tagName);
        boolean isHeading = tagName.matches("h[1-6]");
        boolean isParagraph = "p".equals(tagName);

        // Add appropriate spacing before block elements
        if (isHeading) {
            ensureNewlines(sb, 2);
        } else if (isBlock || isParagraph) {
            ensureNewlines(sb, 1);
        }

        // Handle list items
        if (isListItem) {
            sb.append("• ");
        }

        // Process children
        for (org.jsoup.nodes.Node child : element.childNodes()) {
            if (child instanceof org.jsoup.nodes.TextNode) {
                String text = ((org.jsoup.nodes.TextNode) child).text();
                if (!text.trim().isEmpty()) {
                    sb.append(text);
                }
            } else if (child instanceof Element) {
                extractElementText((Element) child, sb);
            }
        }

        // Add spacing after block elements
        if (isHeading) {
            ensureNewlines(sb, 2);
        } else if (isBlock || isParagraph) {
            ensureNewlines(sb, 1);
        } else if ("br".equals(tagName)) {
            sb.append("\n");
        }
    }

    private boolean isBlockElement(String tagName) {
        return Arrays.asList(
                "div", "p", "h1", "h2", "h3", "h4", "h5", "h6",
                "ul", "ol", "li", "table", "tr", "blockquote",
                "pre", "article", "section", "header", "footer",
                "main", "aside", "nav", "figure", "figcaption"
        ).contains(tagName);
    }

    private void ensureNewlines(StringBuilder sb, int count) {
        // Count existing trailing newlines
        int existingNewlines = 0;
        for (int i = sb.length() - 1; i >= 0 && sb.charAt(i) == '\n'; i--) {
            existingNewlines++;
        }

        // Add needed newlines
        for (int i = existingNewlines; i < count; i++) {
            sb.append("\n");
        }
    }
}

