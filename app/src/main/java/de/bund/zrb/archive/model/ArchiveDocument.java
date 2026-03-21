package de.bund.zrb.archive.model;

import java.util.UUID;

/**
 * @deprecated Use {@link ArchiveEntry} directly. This class is kept only for
 *             backwards compatibility during the migration period.
 *             All catalog data now lives in the unified {@code archive_entries} table.
 */
@Deprecated
public class ArchiveDocument {

    /** Document kind taxonomy (also available as String constants on ArchiveEntry). */
    public enum Kind {
        ARTICLE,
        LISTING,
        PAGE,
        FEED_ENTRY,
        OTHER
    }

    private String docId = UUID.randomUUID().toString();
    private String runId = "";
    private long createdAt;
    private String kind = Kind.PAGE.name();
    private String title = "";
    private String canonicalUrl = "";
    private String sourceResourceIds = "";
    private String excerpt = "";
    private String textContentPath = "";
    private String language = "";
    private long indexedAt;
    private int wordCount;
    private String host = "";

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

    /** Convert this deprecated document to the unified ArchiveEntry. */
    public ArchiveEntry toEntry() {
        ArchiveEntry e = new ArchiveEntry();
        e.setEntryId(docId);
        e.setUrl(canonicalUrl);
        e.setTitle(title);
        e.setCrawlTimestamp(createdAt);
        e.setLastIndexed(indexedAt);
        e.setStatus(ArchiveEntryStatus.INDEXED);
        e.setRunId(runId);
        e.setKind(kind);
        e.setExcerpt(excerpt);
        e.setTextContentPath(textContentPath);
        e.setLanguage(language);
        e.setWordCount(wordCount);
        e.setHost(host);
        e.setSourceResourceIds(sourceResourceIds);
        return e;
    }

    @Override
    public String toString() {
        return "Document[" + docId + " " + kind + " " + title + "]";
    }
}
