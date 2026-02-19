package de.bund.zrb.ingestion.infrastructure.extractor;

import de.bund.zrb.ingestion.model.DetectionResult;
import de.bund.zrb.ingestion.model.DocumentSource;
import de.bund.zrb.ingestion.model.ExtractionResult;
import de.bund.zrb.ingestion.port.TextExtractor;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Extractor for Markdown files.
 * Returns the raw Markdown as plaintext (preserving formatting for AI).
 */
public class MarkdownTextExtractor implements TextExtractor {

    private static final List<String> SUPPORTED_MIME_TYPES = Arrays.asList(
            "text/markdown",
            "text/x-markdown",
            "text/x-web-markdown"
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
        return "MarkdownTextExtractor";
    }

    @Override
    public ExtractionResult extract(DocumentSource source, DetectionResult detection) {
        List<String> warnings = new ArrayList<>();

        try {
            byte[] bytes = source.getBytes();
            if (bytes == null || bytes.length == 0) {
                return ExtractionResult.failure("Datei ist leer", getName());
            }

            // Determine charset
            Charset charset = determineCharset(detection, bytes, warnings);

            // Convert to string
            String markdown = new String(bytes, charset);

            // For AI purposes, we keep the raw Markdown
            // It preserves structure (headings, lists, code blocks) that helps AI understand the document
            return ExtractionResult.success(markdown, warnings, getName());

        } catch (Exception e) {
            return ExtractionResult.failure("Fehler beim Lesen der Markdown-Datei: " + e.getMessage(), getName());
        }
    }

    private Charset determineCharset(DetectionResult detection, byte[] bytes, List<String> warnings) {
        // 1. Try charset from detection
        if (detection != null && detection.getCharset() != null) {
            try {
                return Charset.forName(detection.getCharset());
            } catch (Exception e) {
                warnings.add("Charset '" + detection.getCharset() + "' nicht unterstützt");
            }
        }

        // 2. Check for BOM
        if (bytes.length >= 3 && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF) {
            return StandardCharsets.UTF_8;
        }

        // 3. Default to UTF-8 (most common for Markdown)
        return StandardCharsets.UTF_8;
    }
}

