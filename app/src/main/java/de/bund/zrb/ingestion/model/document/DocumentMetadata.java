package de.bund.zrb.ingestion.model.document;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Metadata for a Document.
 * Immutable.
 */
public final class DocumentMetadata {

    private final String sourceName;
    private final String mimeType;
    private final Integer pageCount;
    private final Map<String, String> attributes;

    private DocumentMetadata(Builder builder) {
        this.sourceName = builder.sourceName;
        this.mimeType = builder.mimeType;
        this.pageCount = builder.pageCount;
        this.attributes = Collections.unmodifiableMap(new LinkedHashMap<>(builder.attributes));
    }

    public String getSourceName() {
        return sourceName;
    }

    public String getMimeType() {
        return mimeType;
    }

    public Integer getPageCount() {
        return pageCount;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public String getAttribute(String key) {
        return attributes.get(key);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return "DocumentMetadata{" +
                "sourceName='" + sourceName + '\'' +
                ", mimeType='" + mimeType + '\'' +
                ", pageCount=" + pageCount +
                ", attributes=" + attributes.size() +
                '}';
    }

    public static class Builder {
        private String sourceName;
        private String mimeType;
        private Integer pageCount;
        private Map<String, String> attributes = new LinkedHashMap<>();

        public Builder sourceName(String sourceName) {
            this.sourceName = sourceName;
            return this;
        }

        public Builder mimeType(String mimeType) {
            this.mimeType = mimeType;
            return this;
        }

        public Builder pageCount(Integer pageCount) {
            this.pageCount = pageCount;
            return this;
        }

        public Builder attribute(String key, String value) {
            this.attributes.put(key, value);
            return this;
        }

        public Builder attributes(Map<String, String> attributes) {
            if (attributes != null) {
                this.attributes.putAll(attributes);
            }
            return this;
        }

        public DocumentMetadata build() {
            return new DocumentMetadata(this);
        }
    }
}

