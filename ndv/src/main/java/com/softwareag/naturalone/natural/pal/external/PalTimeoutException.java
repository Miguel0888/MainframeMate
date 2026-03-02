package com.softwareag.naturalone.natural.pal.external;

public class PalTimeoutException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public PalTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}

