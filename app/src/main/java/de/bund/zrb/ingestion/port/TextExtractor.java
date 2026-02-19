package de.bund.zrb.ingestion.port;

import de.bund.zrb.ingestion.model.DetectionResult;
import de.bund.zrb.ingestion.model.DocumentSource;
import de.bund.zrb.ingestion.model.ExtractionResult;

/**
 * Port interface for text extraction from documents.
 * Implementations extract plaintext from specific document formats.
 */
public interface TextExtractor {

    /**
     * Check if this extractor can handle the given MIME type.
     *
     * @param mimeType the MIME type to check
     * @return true if this extractor supports the MIME type
     */
    boolean supports(String mimeType);

    /**
     * Get the priority of this extractor (higher = preferred).
     * Used when multiple extractors support the same MIME type.
     *
     * @return priority value (default: 0)
     */
    default int getPriority() {
        return 0;
    }

    /**
     * Get the name of this extractor for logging/debugging.
     *
     * @return extractor name
     */
    String getName();

    /**
     * Extract plaintext from a document.
     *
     * @param source the document source
     * @param detection the content type detection result
     * @return extraction result containing plaintext or error
     */
    ExtractionResult extract(DocumentSource source, DetectionResult detection);
}

