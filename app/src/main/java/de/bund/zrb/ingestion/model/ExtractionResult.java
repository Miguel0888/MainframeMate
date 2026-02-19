package de.bund.zrb.ingestion.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Result of text extraction from a document.
 * Contains the extracted plaintext, warnings, and optional metadata.
 */
public class ExtractionResult {

    private final String plainText;
    private final List<String> warnings;
    private final Map<String, String> metadata;
    private final String extractorName;
    private final boolean success;
    private final String errorMessage;

    private ExtractionResult(String plainText, List<String> warnings, Map<String, String> metadata,
                              String extractorName, boolean success, String errorMessage) {
        this.plainText = plainText;
        this.warnings = warnings != null ? new ArrayList<>(warnings) : new ArrayList<>();
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
        this.extractorName = extractorName;
        this.success = success;
        this.errorMessage = errorMessage;
    }

    /**
     * Create a successful extraction result.
     */
    public static ExtractionResult success(String plainText, String extractorName) {
        return new ExtractionResult(plainText, null, null, extractorName, true, null);
    }

    /**
     * Create a successful extraction result with warnings.
     */
    public static ExtractionResult success(String plainText, List<String> warnings, String extractorName) {
        return new ExtractionResult(plainText, warnings, null, extractorName, true, null);
    }

    /**
     * Create a successful extraction result with warnings and metadata.
     */
    public static ExtractionResult success(String plainText, List<String> warnings,
                                            Map<String, String> metadata, String extractorName) {
        return new ExtractionResult(plainText, warnings, metadata, extractorName, true, null);
    }

    /**
     * Create a failed extraction result.
     */
    public static ExtractionResult failure(String errorMessage, String extractorName) {
        return new ExtractionResult(null, null, null, extractorName, false, errorMessage);
    }

    /**
     * Create a failed extraction result with warnings.
     */
    public static ExtractionResult failure(String errorMessage, List<String> warnings, String extractorName) {
        return new ExtractionResult(null, warnings, null, extractorName, false, errorMessage);
    }

    public String getPlainText() {
        return plainText;
    }

    public List<String> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    public Map<String, String> getMetadata() {
        return Collections.unmodifiableMap(metadata);
    }

    public String getExtractorName() {
        return extractorName;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    public void addWarning(String warning) {
        warnings.add(warning);
    }

    public void addMetadata(String key, String value) {
        metadata.put(key, value);
    }

    /**
     * Create a copy with additional warnings.
     */
    public ExtractionResult withAdditionalWarnings(List<String> additionalWarnings) {
        List<String> allWarnings = new ArrayList<>(this.warnings);
        allWarnings.addAll(additionalWarnings);
        return new ExtractionResult(plainText, allWarnings, metadata, extractorName, success, errorMessage);
    }

    /**
     * Create a copy with normalized text.
     */
    public ExtractionResult withNormalizedText(String normalizedText) {
        return new ExtractionResult(normalizedText, warnings, metadata, extractorName, success, errorMessage);
    }

    @Override
    public String toString() {
        return "ExtractionResult{" +
                "success=" + success +
                ", extractorName='" + extractorName + '\'' +
                ", textLength=" + (plainText != null ? plainText.length() : 0) +
                ", warnings=" + warnings.size() +
                '}';
    }
}

