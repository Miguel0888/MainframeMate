package de.bund.zrb.ingestion.port;

/**
 * Port interface for text post-processing/normalization.
 * Implementations clean and normalize extracted text.
 */
public interface TextPostProcessor {

    /**
     * Process/normalize the extracted text.
     *
     * @param text the raw extracted text
     * @return normalized text
     */
    String process(String text);
}

