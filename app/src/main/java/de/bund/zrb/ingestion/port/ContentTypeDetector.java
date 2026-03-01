package de.bund.zrb.ingestion.port;

import de.bund.zrb.ingestion.model.DetectionResult;
import de.bund.zrb.ingestion.model.DocumentSource;

/**
 * Port interface for content typSchluessel detection.
 * Implementations detect the MIME typSchluessel of a document from its bytes.
 */
public interface ContentTypeDetector {

    /**
     * Detect the content typSchluessel of a document.
     *
     * @param source the document source containing bytes and optional hints
     * @return detection result with MIME typSchluessel and confidence
     */
    DetectionResult detect(DocumentSource source);
}

