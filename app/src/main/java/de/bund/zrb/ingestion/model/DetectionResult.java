package de.bund.zrb.ingestion.model;

/**
 * Result of content type detection.
 * Contains detected MIME type and confidence indicator.
 */
public class DetectionResult {

    private final String mimeType;
    private final String charset;
    private final double confidence;
    private final String detectorName;

    public DetectionResult(String mimeType, String charset, double confidence, String detectorName) {
        this.mimeType = mimeType;
        this.charset = charset;
        this.confidence = confidence;
        this.detectorName = detectorName;
    }

    public DetectionResult(String mimeType) {
        this(mimeType, null, 1.0, "unknown");
    }

    public DetectionResult(String mimeType, String charset) {
        this(mimeType, charset, 1.0, "unknown");
    }

    public String getMimeType() {
        return mimeType;
    }

    public String getCharset() {
        return charset;
    }

    public double getConfidence() {
        return confidence;
    }

    public String getDetectorName() {
        return detectorName;
    }

    /**
     * Returns the base MIME type without parameters.
     */
    public String getBaseMimeType() {
        if (mimeType == null) return null;
        int semicolon = mimeType.indexOf(';');
        return semicolon > 0 ? mimeType.substring(0, semicolon).trim() : mimeType;
    }

    /**
     * Check if this is a text-based MIME type.
     */
    public boolean isTextBased() {
        if (mimeType == null) return false;
        return mimeType.startsWith("text/") ||
               mimeType.contains("json") ||
               mimeType.contains("xml") ||
               mimeType.contains("javascript") ||
               mimeType.contains("markdown");
    }

    /**
     * Check if this is an image MIME type.
     */
    public boolean isImage() {
        return mimeType != null && mimeType.startsWith("image/");
    }

    /**
     * Check if this is a binary/unknown type.
     */
    public boolean isUnknownBinary() {
        return "application/octet-stream".equals(mimeType);
    }

    @Override
    public String toString() {
        return "DetectionResult{" +
                "mimeType='" + mimeType + '\'' +
                ", charset='" + charset + '\'' +
                ", confidence=" + confidence +
                '}';
    }
}

