package de.bund.zrb.betaview.infrastructure;
/**
 * Result of a document download - carries the raw bytes together with
 * the content type and filename reported by the server.
 */
public final class DownloadResult {
    private final byte[] data;
    private final String filename;
    private final String contentType;
    public DownloadResult(byte[] data, String filename, String contentType) {
        this.data = data != null ? data : new byte[0];
        this.filename = filename != null ? filename : "";
        this.contentType = contentType != null ? contentType : "";
    }
    public byte[] data()        { return data; }
    public String filename()    { return filename; }
    public String contentType() { return contentType; }
    public boolean isPdf() {
        return contentType.contains("pdf");
    }
    public boolean isText() {
        return contentType.contains("text/plain")
            || contentType.contains("text/csv")
            || contentType.contains("application/octet-stream");
    }
}