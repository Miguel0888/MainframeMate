package de.bund.zrb.files.impl.ftp.jes;

/**
 * Thrown when a JCL job submission via FTP JES fails.
 */
public class JesSubmitException extends Exception {

    public JesSubmitException(String message) {
        super(message);
    }

    public JesSubmitException(String message, Throwable cause) {
        super(message, cause);
    }
}

