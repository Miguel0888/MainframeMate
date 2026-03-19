package de.zrb.bund.newApi.browser;

/**
 * Exception thrown by Browser API operations.
 */
public class BrowserException extends RuntimeException {

    public BrowserException(String message) {
        super(message);
    }

    public BrowserException(String message, Throwable cause) {
        super(message, cause);
    }
}

