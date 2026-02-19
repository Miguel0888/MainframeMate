package de.bund.zrb.ingestion.model;

/**
 * Represents the source of a document to be processed.
 * Contains the raw bytes and optional hints (filename, path).
 */
public class DocumentSource {

    private final byte[] bytes;
    private final String resourceName;
    private final String path;

    private DocumentSource(byte[] bytes, String resourceName, String path) {
        this.bytes = bytes;
        this.resourceName = resourceName;
        this.path = path;
    }

    /**
     * Create a DocumentSource from raw bytes with a resource name hint.
     */
    public static DocumentSource fromBytes(byte[] bytes, String resourceName) {
        return new DocumentSource(bytes, resourceName, null);
    }

    /**
     * Create a DocumentSource from raw bytes without hints.
     */
    public static DocumentSource fromBytes(byte[] bytes) {
        return new DocumentSource(bytes, null, null);
    }

    /**
     * Create a DocumentSource with full path information.
     */
    public static DocumentSource fromPath(byte[] bytes, String path) {
        String resourceName = path;
        if (path != null) {
            int lastSep = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
            if (lastSep >= 0 && lastSep < path.length() - 1) {
                resourceName = path.substring(lastSep + 1);
            }
        }
        return new DocumentSource(bytes, resourceName, path);
    }

    public byte[] getBytes() {
        return bytes;
    }

    public String getResourceName() {
        return resourceName;
    }

    public String getPath() {
        return path;
    }

    public int getSize() {
        return bytes != null ? bytes.length : 0;
    }

    public boolean isEmpty() {
        return bytes == null || bytes.length == 0;
    }
}

