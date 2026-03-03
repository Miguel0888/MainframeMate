package de.bund.zrb.ndv.core.api;

public class PalTimeoutException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public PalTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}

