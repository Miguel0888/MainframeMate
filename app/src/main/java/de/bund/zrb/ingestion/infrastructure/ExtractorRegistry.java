package de.bund.zrb.ingestion.infrastructure;

import de.bund.zrb.ingestion.infrastructure.extractor.*;
import de.bund.zrb.ingestion.port.TextExtractor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Registry for text extractors.
 * Routes documents to appropriate extractors based on MIME type and priority.
 */
public class ExtractorRegistry {

    private final List<TextExtractor> extractors;
    private final TikaFallbackExtractor fallbackExtractor;

    public ExtractorRegistry() {
        this.extractors = new ArrayList<>();
        this.fallbackExtractor = new TikaFallbackExtractor();

        // Register default extractors
        registerDefaultExtractors();
    }

    private void registerDefaultExtractors() {
        // Register specialized extractors (higher priority)
        register(new PlainTextExtractor());
        register(new MarkdownTextExtractor());
        register(new HtmlTextExtractor());
        register(new PdfTextExtractor());
        register(new DocxTextExtractor());

        // Register fallback extractor (lowest priority)
        register(fallbackExtractor);
    }

    /**
     * Register a new extractor.
     * Extractors are sorted by priority (descending) after registration.
     */
    public void register(TextExtractor extractor) {
        extractors.add(extractor);
        // Sort by priority (highest first)
        Collections.sort(extractors, new Comparator<TextExtractor>() {
            @Override
            public int compare(TextExtractor a, TextExtractor b) {
                return Integer.compare(b.getPriority(), a.getPriority());
            }
        });
    }

    /**
     * Find the best extractor for the given MIME type.
     * Returns the highest-priority extractor that supports the MIME type.
     *
     * @param mimeType the MIME type to find an extractor for
     * @return the best matching extractor, or fallback if none found
     */
    public TextExtractor findExtractor(String mimeType) {
        for (TextExtractor extractor : extractors) {
            if (extractor.supports(mimeType)) {
                return extractor;
            }
        }
        // Should not happen since fallback supports everything
        return fallbackExtractor;
    }

    /**
     * Find all extractors that support the given MIME type.
     *
     * @param mimeType the MIME type
     * @return list of supporting extractors, sorted by priority
     */
    public List<TextExtractor> findAllExtractors(String mimeType) {
        List<TextExtractor> matching = new ArrayList<>();
        for (TextExtractor extractor : extractors) {
            if (extractor.supports(mimeType)) {
                matching.add(extractor);
            }
        }
        return matching;
    }

    /**
     * Get all registered extractors.
     */
    public List<TextExtractor> getAllExtractors() {
        return Collections.unmodifiableList(extractors);
    }

    /**
     * Get the fallback extractor (Tika).
     */
    public TikaFallbackExtractor getFallbackExtractor() {
        return fallbackExtractor;
    }

    /**
     * Clear all extractors and re-register defaults.
     */
    public void reset() {
        extractors.clear();
        registerDefaultExtractors();
    }

    /**
     * Remove an extractor by name.
     */
    public boolean removeExtractor(String name) {
        return extractors.removeIf(e -> e.getName().equals(name));
    }
}

