package de.bund.zrb.files.api;

import de.bund.zrb.files.model.FilePayload;

public class FileWriteResult {

    public enum Status {
        SUCCESS,
        CONFLICT
    }

    private final Status status;
    private final FilePayload currentRemote;

    private FileWriteResult(Status status, FilePayload currentRemote) {
        this.status = status;
        this.currentRemote = currentRemote;
    }

    public static FileWriteResult success() {
        return new FileWriteResult(Status.SUCCESS, null);
    }

    public static FileWriteResult conflict(FilePayload currentRemote) {
        return new FileWriteResult(Status.CONFLICT, currentRemote);
    }

    public Status getStatus() {
        return status;
    }

    public FilePayload getCurrentRemote() {
        return currentRemote;
    }
}

