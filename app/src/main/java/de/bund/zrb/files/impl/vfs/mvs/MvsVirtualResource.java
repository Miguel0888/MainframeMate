package de.bund.zrb.files.impl.vfs.mvs;

/**
 * A virtual resource representing an MVS dataset/member/HLQ.
 * This is what the UI works with - no MVS knowledge needed in UI code.
 */
public final class MvsVirtualResource {

    private final MvsLocation location;
    private final long size;
    private final long lastModified;
    private final String recordFormat;
    private final int logicalRecordLength;

    private MvsVirtualResource(Builder builder) {
        this.location = builder.location;
        this.size = builder.size;
        this.lastModified = builder.lastModified;
        this.recordFormat = builder.recordFormat;
        this.logicalRecordLength = builder.logicalRecordLength;
    }

    /**
     * Get the display name for UI.
     */
    public String getDisplayName() {
        return location.getDisplayName();
    }

    /**
     * Get the full path for opening/navigation.
     */
    public String getOpenPath() {
        return location.getLogicalPath();
    }

    /**
     * Get the location type (HLQ/DATASET/MEMBER).
     */
    public MvsLocationType getType() {
        return location.getType();
    }

    /**
     * Get the underlying MvsLocation.
     */
    public MvsLocation getLocation() {
        return location;
    }

    /**
     * Check if this resource is a "directory" (can be navigated into).
     */
    public boolean isDirectory() {
        return location.isDirectory();
    }

    /**
     * Get file size (0 if not available).
     */
    public long getSize() {
        return size;
    }

    /**
     * Get last modified timestamp (0 if not available).
     */
    public long getLastModified() {
        return lastModified;
    }

    /**
     * Get record format (RECFM) if available.
     */
    public String getRecordFormat() {
        return recordFormat;
    }

    /**
     * Get logical record length (LRECL) if available.
     */
    public int getLogicalRecordLength() {
        return logicalRecordLength;
    }

    /**
     * Get unique key for deduplication.
     */
    public String getKey() {
        return location.getLogicalPath().toUpperCase();
    }

    @Override
    public String toString() {
        return "MvsVirtualResource{" + location + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MvsVirtualResource that = (MvsVirtualResource) o;
        return location.equals(that.location);
    }

    @Override
    public int hashCode() {
        return location.hashCode();
    }

    // Builder

    public static Builder builder(MvsLocation location) {
        return new Builder(location);
    }

    public static class Builder {
        private final MvsLocation location;
        private long size = 0;
        private long lastModified = 0;
        private String recordFormat = null;
        private int logicalRecordLength = 0;

        private Builder(MvsLocation location) {
            this.location = location;
        }

        public Builder size(long size) {
            this.size = size;
            return this;
        }

        public Builder lastModified(long lastModified) {
            this.lastModified = lastModified;
            return this;
        }

        public Builder recordFormat(String recordFormat) {
            this.recordFormat = recordFormat;
            return this;
        }

        public Builder logicalRecordLength(int logicalRecordLength) {
            this.logicalRecordLength = logicalRecordLength;
            return this;
        }

        public MvsVirtualResource build() {
            return new MvsVirtualResource(this);
        }
    }
}

