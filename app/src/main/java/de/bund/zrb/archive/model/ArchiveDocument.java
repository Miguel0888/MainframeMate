package de.bund.zrb.archive.model;

import java.util.UUID;

/**
 * A curated, bot-friendly catalog entry derived from one or more ArchiveResources.
 * Documents are always indexable and provide clean title, excerpt, and extracted text.
 */
public class ArchiveDocument {

    /** Document kind taxonomy. */
    public enum Kind {
        ARTICLE,
        LISTING,
        PAGE,
        FEED_ENTRY,
        OTHER
    }

    private String docId = UUID.randomUUID().toString();
    private String runId = "";
    private long createdAt;                     // epoch millis UTC
    private String kind = Kind.PAGE.name();     // Kind.name()
    private String title = "";                  // stable title, NEVER just the URL
    private String canonicalUrl = "";
    private String sourceResourceIds = "";      // comma-separated resource IDs
    private String excerpt = "";                // short text (max ~1200 chars)
    private String textContentPath = "";        // path to extracted text file (for Lucene)
    private String language = "";               // heuristic language detection
    private long indexedAt;                     // epoch millis of last Lucene indexing
    private int wordCount;
    private String host = "";                   // for filtering in UI

    // ── Getters & Setters ──

    public String getDocId() { return docId; }
    public void setDocId(String docId) { this.docId = docId; }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getCanonicalUrl() { return canonicalUrl; }
    public void setCanonicalUrl(String canonicalUrl) { this.canonicalUrl = canonicalUrl; }

    public String getSourceResourceIds() { return sourceResourceIds; }
    public void setSourceResourceIds(String sourceResourceIds) { this.sourceResourceIds = sourceResourceIds; }

    public String getExcerpt() { return excerpt; }
    public void setExcerpt(String excerpt) { this.excerpt = excerpt; }

    public String getTextContentPath() { return textContentPath; }
    public void setTextContentPath(String textContentPath) { this.textContentPath = textContentPath; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public long getIndexedAt() { return indexedAt; }
    public void setIndexedAt(long indexedAt) { this.indexedAt = indexedAt; }

    public int getWordCount() { return wordCount; }
    public void setWordCount(int wordCount) { this.wordCount = wordCount; }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    @Override
    public String toString() {
        return "Document[" + docId + " " + kind + " " + title + "]";
    }
}
