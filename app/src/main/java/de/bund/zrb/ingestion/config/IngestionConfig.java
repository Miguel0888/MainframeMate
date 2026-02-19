package de.bund.zrb.ingestion.config;

import java.util.HashSet;
import java.util.Set;

/**
 * Configuration for the document ingestion pipeline.
 */
public class IngestionConfig {

    // Size limits
    private long maxFileSizeBytes = 25 * 1024 * 1024; // 25 MB default

    // Timeout
    private long timeoutPerExtractionMs = 10000; // 10 seconds default

    // Policy flags
    private boolean allowOctetStream = false;
    private boolean enableFallbackOnExtractorFailure = true;

    // MIME type filters
    private Set<String> rejectedMimeTypes = new HashSet<>();
    private Set<String> acceptedMimeTypes = new HashSet<>(); // empty = accept all not rejected

    // Default rejected types
    public IngestionConfig() {
        // Reject executable/active content by default
        rejectedMimeTypes.add("application/x-msdownload");
        rejectedMimeTypes.add("application/x-executable");
        rejectedMimeTypes.add("application/x-msdos-program");
        rejectedMimeTypes.add("application/x-sh");
        rejectedMimeTypes.add("application/x-bat");
        rejectedMimeTypes.add("application/java-archive");
        rejectedMimeTypes.add("application/x-java-class");
    }

    // Getters and setters

    public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    public IngestionConfig setMaxFileSizeBytes(long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
        return this;
    }

    public long getTimeoutPerExtractionMs() {
        return timeoutPerExtractionMs;
    }

    public IngestionConfig setTimeoutPerExtractionMs(long timeoutPerExtractionMs) {
        this.timeoutPerExtractionMs = timeoutPerExtractionMs;
        return this;
    }

    public boolean isAllowOctetStream() {
        return allowOctetStream;
    }

    public IngestionConfig setAllowOctetStream(boolean allowOctetStream) {
        this.allowOctetStream = allowOctetStream;
        return this;
    }

    public boolean isEnableFallbackOnExtractorFailure() {
        return enableFallbackOnExtractorFailure;
    }

    public IngestionConfig setEnableFallbackOnExtractorFailure(boolean enableFallbackOnExtractorFailure) {
        this.enableFallbackOnExtractorFailure = enableFallbackOnExtractorFailure;
        return this;
    }

    public Set<String> getRejectedMimeTypes() {
        return rejectedMimeTypes;
    }

    public IngestionConfig setRejectedMimeTypes(Set<String> rejectedMimeTypes) {
        this.rejectedMimeTypes = rejectedMimeTypes;
        return this;
    }

    public IngestionConfig addRejectedMimeType(String mimeType) {
        this.rejectedMimeTypes.add(mimeType);
        return this;
    }

    public Set<String> getAcceptedMimeTypes() {
        return acceptedMimeTypes;
    }

    public IngestionConfig setAcceptedMimeTypes(Set<String> acceptedMimeTypes) {
        this.acceptedMimeTypes = acceptedMimeTypes;
        return this;
    }

    public IngestionConfig addAcceptedMimeType(String mimeType) {
        this.acceptedMimeTypes.add(mimeType);
        return this;
    }

    /**
     * Check if a MIME type is explicitly rejected.
     */
    public boolean isRejectedMimeType(String mimeType) {
        if (mimeType == null) return false;
        String baseMime = mimeType.contains(";") ? mimeType.substring(0, mimeType.indexOf(';')).trim() : mimeType;
        return rejectedMimeTypes.contains(baseMime);
    }

    /**
     * Check if a MIME type is explicitly accepted.
     * Returns true if acceptedMimeTypes is empty (accept all).
     */
    public boolean isAcceptedMimeType(String mimeType) {
        if (acceptedMimeTypes.isEmpty()) return true;
        if (mimeType == null) return false;
        String baseMime = mimeType.contains(";") ? mimeType.substring(0, mimeType.indexOf(';')).trim() : mimeType;
        return acceptedMimeTypes.contains(baseMime);
    }
}

