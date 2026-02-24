package de.bund.zrb.websearch.fetch;

import java.util.Collections;
import java.util.List;

/**
 * Result of a content fetch operation.
 * Contains the extracted text, title, URL, links and metadata.
 */
public class FetchResult {

    public enum Source {
        JSOUP,
        BROWSER,
        FAILED
    }

    private final boolean success;
    private final Source source;
    private final String url;
    private final String title;
    private final String text;
    private final String metaDescription;
    private final List<Link> links;
    private final int statusCode;
    private final String errorMessage;

    private FetchResult(Builder builder) {
        this.success = builder.success;
        this.source = builder.source;
        this.url = builder.url;
        this.title = builder.title;
        this.text = builder.text;
        this.metaDescription = builder.metaDescription;
        this.links = builder.links != null ? builder.links : Collections.<Link>emptyList();
        this.statusCode = builder.statusCode;
        this.errorMessage = builder.errorMessage;
    }

    public boolean isSuccess() { return success; }
    public Source getSource() { return source; }
    public String getUrl() { return url; }
    public String getTitle() { return title; }
    public String getText() { return text; }
    public String getMetaDescription() { return metaDescription; }
    public List<Link> getLinks() { return links; }
    public int getStatusCode() { return statusCode; }
    public String getErrorMessage() { return errorMessage; }

    /**
     * Returns true if the fetched text content is substantial enough
     * to be considered a successful extraction (not just boilerplate/JS-only page).
     */
    public boolean hasSubstantialContent() {
        if (text == null) return false;
        String cleaned = text.replaceAll("\\s+", " ").trim();
        return cleaned.length() > 200;
    }

    /**
     * Formats the result as a human-readable string for MCP tool output.
     */
    public String toFormattedString(int maxTextLength) {
        StringBuilder sb = new StringBuilder();
        sb.append("Page: ").append(title != null ? title : "(no title)").append("\n");
        sb.append("URL: ").append(url != null ? url : "(unknown)").append("\n");
        sb.append("Source: ").append(source).append("\n");
        if (metaDescription != null && !metaDescription.isEmpty()) {
            sb.append("Description: ").append(metaDescription).append("\n");
        }
        sb.append("────────────────────────────────────────\n");
        if (text != null && !text.isEmpty()) {
            String truncated = text.length() > maxTextLength
                    ? text.substring(0, maxTextLength) + "\n[… truncated at " + maxTextLength + " chars]"
                    : text;
            sb.append(truncated);
        } else {
            sb.append("(No readable text found on this page)");
        }
        return sb.toString();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static FetchResult error(String url, String errorMessage) {
        return new Builder()
                .success(false)
                .source(Source.FAILED)
                .url(url)
                .errorMessage(errorMessage)
                .build();
    }

    /**
     * A link extracted from the page.
     */
    public static class Link {
        private final String url;
        private final String text;

        public Link(String url, String text) {
            this.url = url;
            this.text = text;
        }

        public String getUrl() { return url; }
        public String getText() { return text; }

        @Override
        public String toString() {
            return text + " -> " + url;
        }
    }

    public static class Builder {
        private boolean success;
        private Source source;
        private String url;
        private String title;
        private String text;
        private String metaDescription;
        private List<Link> links;
        private int statusCode;
        private String errorMessage;

        public Builder success(boolean success) { this.success = success; return this; }
        public Builder source(Source source) { this.source = source; return this; }
        public Builder url(String url) { this.url = url; return this; }
        public Builder title(String title) { this.title = title; return this; }
        public Builder text(String text) { this.text = text; return this; }
        public Builder metaDescription(String metaDescription) { this.metaDescription = metaDescription; return this; }
        public Builder links(List<Link> links) { this.links = links; return this; }
        public Builder statusCode(int statusCode) { this.statusCode = statusCode; return this; }
        public Builder errorMessage(String errorMessage) { this.errorMessage = errorMessage; return this; }

        public FetchResult build() {
            return new FetchResult(this);
        }
    }
}
