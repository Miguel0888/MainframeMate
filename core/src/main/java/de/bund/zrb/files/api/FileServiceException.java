package de.bund.zrb.files.api;

public class FileServiceException extends Exception {

    private final FileServiceErrorCode errorCode;

    public FileServiceException(FileServiceErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode == null ? FileServiceErrorCode.UNKNOWN : errorCode;
    }

    public FileServiceException(FileServiceErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode == null ? FileServiceErrorCode.UNKNOWN : errorCode;
    }

    public FileServiceErrorCode getErrorCode() {
        return errorCode;
    }
}

