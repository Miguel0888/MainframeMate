package de.bund.zrb.ingestion.port;

import de.bund.zrb.ingestion.model.AcceptanceDecision;
import de.bund.zrb.ingestion.model.DetectionResult;
import de.bund.zrb.ingestion.model.DocumentSource;

/**
 * Port interface for document acceptance policy.
 * Decides whether a document should be processed or rejected.
 */
public interface DocumentAcceptancePolicy {

    /**
     * Evaluate whether a document should be accepted for processing.
     *
     * @param source the document source
     * @param detection the content type detection result
     * @return acceptance decision (ACCEPT or REJECT with reason)
     */
    AcceptanceDecision evaluate(DocumentSource source, DetectionResult detection);
}

