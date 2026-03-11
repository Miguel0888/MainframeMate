package com.acme.betaview;

/**
 * Result of a document download – carries the raw bytes together with
 * the content type reported by the server.
 */
final class DownloadResult {

    private final byte[] data;
    private final String contentType;

    DownloadResult(byte[] data, String contentType) {
        this.data = data != null ? data : new byte[0];
        this.contentType = contentType != null ? contentType : "";
    }

    byte[] data()        { return data; }
    String contentType() { return contentType; }

    boolean isPdf() {
        return contentType.contains("pdf");
    }

    boolean isText() {
        return contentType.contains("text/plain")
            || contentType.contains("text/csv")
            || contentType.contains("application/octet-stream"); // server sometimes sends this for txt
    }
}

