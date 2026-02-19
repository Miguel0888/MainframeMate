package de.bund.zrb.ingestion.infrastructure;

import de.bund.zrb.ingestion.config.IngestionConfig;
import de.bund.zrb.ingestion.model.AcceptanceDecision;
import de.bund.zrb.ingestion.model.DetectionResult;
import de.bund.zrb.ingestion.model.DocumentSource;
import de.bund.zrb.ingestion.port.DocumentAcceptancePolicy;

/**
 * Default document acceptance policy implementation.
 * Evaluates documents based on size, MIME type, and other criteria.
 */
public class DefaultAcceptancePolicy implements DocumentAcceptancePolicy {

    private final IngestionConfig config;

    public DefaultAcceptancePolicy(IngestionConfig config) {
        this.config = config;
    }

    public DefaultAcceptancePolicy() {
        this(new IngestionConfig());
    }

    @Override
    public AcceptanceDecision evaluate(DocumentSource source, DetectionResult detection) {
        String mimeType = detection != null ? detection.getMimeType() : null;

        // Check: empty or null
        if (source == null || source.isEmpty()) {
            return AcceptanceDecision.reject("Datei ist leer oder nicht lesbar", mimeType);
        }

        // Check: file size limit
        if (source.getSize() > config.getMaxFileSizeBytes()) {
            return AcceptanceDecision.reject(
                    String.format("Datei zu groß: %d Bytes (Maximum: %d Bytes)",
                            source.getSize(), config.getMaxFileSizeBytes()),
                    mimeType);
        }

        // Check: detection result available
        if (detection == null || mimeType == null) {
            return AcceptanceDecision.reject("Dateityp konnte nicht erkannt werden", null);
        }

        String baseMimeType = detection.getBaseMimeType();

        // Check: explicitly rejected MIME types
        if (config.isRejectedMimeType(baseMimeType)) {
            return AcceptanceDecision.reject(
                    "Dateityp ist nicht erlaubt: " + baseMimeType, mimeType);
        }

        // Check: unknown binary (application/octet-stream)
        if (detection.isUnknownBinary() && !config.isAllowOctetStream()) {
            return AcceptanceDecision.reject(
                    "Unbekannter Binärdateityp nicht erlaubt", mimeType);
        }

        // Check: images without OCR support
        if (detection.isImage()) {
            return AcceptanceDecision.reject(
                    "Bilddateien werden derzeit nicht unterstützt (OCR nicht aktiviert)", mimeType);
        }

        // Check: executable content
        if (isExecutableContent(baseMimeType)) {
            return AcceptanceDecision.reject(
                    "Ausführbare Dateien sind nicht erlaubt", mimeType);
        }

        // Check: accepted MIME types filter (if configured)
        if (!config.isAcceptedMimeType(baseMimeType)) {
            return AcceptanceDecision.reject(
                    "Dateityp ist nicht in der Liste der akzeptierten Typen: " + baseMimeType, mimeType);
        }

        // All checks passed
        return AcceptanceDecision.accept(mimeType);
    }

    private boolean isExecutableContent(String mimeType) {
        if (mimeType == null) return false;
        return mimeType.contains("executable") ||
               mimeType.contains("x-msdownload") ||
               mimeType.contains("x-msdos-program") ||
               mimeType.contains("x-sh") ||
               mimeType.contains("x-bat") ||
               mimeType.contains("x-java-class") ||
               mimeType.equals("application/java-archive");
    }
}

