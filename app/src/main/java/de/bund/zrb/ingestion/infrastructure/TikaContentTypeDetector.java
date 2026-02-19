package de.bund.zrb.ingestion.infrastructure;

import de.bund.zrb.ingestion.model.DetectionResult;
import de.bund.zrb.ingestion.model.DocumentSource;
import de.bund.zrb.ingestion.port.ContentTypeDetector;
import org.apache.tika.Tika;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.Detector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Content type detector using Apache Tika.
 * Provides robust MIME type detection based on file content and hints.
 */
public class TikaContentTypeDetector implements ContentTypeDetector {

    private final Detector detector;
    private final Tika tika;

    public TikaContentTypeDetector() {
        try {
            TikaConfig config = TikaConfig.getDefaultConfig();
            this.detector = config.getDetector();
            this.tika = new Tika(config);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Tika detector", e);
        }
    }

    @Override
    public DetectionResult detect(DocumentSource source) {
        if (source == null || source.isEmpty()) {
            return new DetectionResult("application/octet-stream", null, 0.0, "TikaDetector");
        }

        try {
            Metadata metadata = new Metadata();

            // Add resource name hint if available
            if (source.getResourceName() != null && !source.getResourceName().isEmpty()) {
                metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, source.getResourceName());
            }

            // Detect using Tika
            try (InputStream is = TikaInputStream.get(new ByteArrayInputStream(source.getBytes()))) {
                MediaType mediaType = detector.detect(is, metadata);

                String mimeType = mediaType.toString();
                String charset = mediaType.getParameters().get("charset");

                // Determine confidence based on detection method
                double confidence = 1.0;
                if ("application/octet-stream".equals(mimeType)) {
                    confidence = 0.1; // Low confidence for unknown binary
                } else if (mimeType.startsWith("text/")) {
                    confidence = 0.9; // High confidence for text types
                }

                return new DetectionResult(mimeType, charset, confidence, "TikaDetector");
            }

        } catch (IOException e) {
            // Fallback to basic detection on error
            return new DetectionResult("application/octet-stream", null, 0.0, "TikaDetector");
        }
    }

    /**
     * Simple string-based detection (alternative method).
     */
    public String detectMimeType(byte[] bytes, String filename) {
        try {
            if (filename != null && !filename.isEmpty()) {
                return tika.detect(bytes, filename);
            } else {
                return tika.detect(bytes);
            }
        } catch (Exception e) {
            return "application/octet-stream";
        }
    }
}

