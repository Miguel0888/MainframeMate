package de.bund.zrb.ndv;

/**
 * Exception for NDV (Natural Development Server) operations.
 */
public class NdvException extends Exception {

    public NdvException(String message) {
        super(message);
    }

    public NdvException(String message, Throwable cause) {
        super(message, cause);
    }
}

