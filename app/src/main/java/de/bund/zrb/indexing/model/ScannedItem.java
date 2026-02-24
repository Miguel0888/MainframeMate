package de.bund.zrb.indexing.model;

/**
 * A single item discovered during a source scan.
 * Lightweight – only metadata needed for delta detection.
 */
public class ScannedItem {

    private final String path;
    private final long lastModified; // epoch millis
    private final long size;         // bytes
    private final boolean directory;
    private final String mimeType;   // optional, may be null

    public ScannedItem(String path, long lastModified, long size, boolean directory, String mimeType) {
        this.path = path;
        this.lastModified = lastModified;
        this.size = size;
        this.directory = directory;
        this.mimeType = mimeType;
    }

    public ScannedItem(String path, long lastModified, long size) {
        this(path, lastModified, size, false, null);
    }

    public String getPath() { return path; }
    public long getLastModified() { return lastModified; }
    public long getSize() { return size; }
    public boolean isDirectory() { return directory; }
    public String getMimeType() { return mimeType; }

    @Override
    public String toString() {
        return path + " (size=" + size + ", modified=" + lastModified + ")";
    }
}
