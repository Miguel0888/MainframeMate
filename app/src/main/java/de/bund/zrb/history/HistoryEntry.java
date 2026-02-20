package de.bund.zrb.history;

/**
 * Represents a single version entry in the local history.
 */
public final class HistoryEntry {

    private final String versionId;
    private final String filePath;
    private final String backend; // LOCAL, FTP, NDV
    private final long timestamp;
    private final int contentLength;
    private final String label;

    public HistoryEntry(String versionId, String filePath, String backend,
                        long timestamp, int contentLength, String label) {
        this.versionId = versionId;
        this.filePath = filePath;
        this.backend = backend;
        this.timestamp = timestamp;
        this.contentLength = contentLength;
        this.label = label;
    }

    public String getVersionId() { return versionId; }
    public String getFilePath() { return filePath; }
    public String getBackend() { return backend; }
    public long getTimestamp() { return timestamp; }
    public int getContentLength() { return contentLength; }
    public String getLabel() { return label; }

    @Override
    public String toString() {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
        String date = sdf.format(new java.util.Date(timestamp));
        String sizeInfo = contentLength > 0 ? " (" + contentLength + " Zeichen)" : "";
        String labelInfo = (label != null && !label.isEmpty()) ? " – " + label : "";
        return date + sizeInfo + labelInfo;
    }
}

