package de.bund.zrb.ingestion.port;

import de.bund.zrb.ingestion.model.DetectionResult;
import de.bund.zrb.ingestion.model.DocumentSource;

/**
 * Port interface for content type detection.
 * Implementations detect the MIME type of a document from its bytes.
 */
public interface ContentTypeDetector {

    /**
     * Detect the content type of a document.
     *
     * @param source the document source containing bytes and optional hints
     * @return detection result with MIME type and confidence
     */
    DetectionResult detect(DocumentSource source);
}

