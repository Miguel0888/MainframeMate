package de.bund.zrb.search;

/**
 * A single search result from any source.
 */
public class SearchResult implements Comparable<SearchResult> {

    public enum SourceType {
        LOCAL("📁", "Lokal"),
        FTP("🌐", "FTP"),
        NDV("🔗", "NDV"),
        MAIL("📧", "Mail"),
        RAG("🤖", "RAG"),
        ARCHIVE("📦", "Archiv");

        private final String icon;
        private final String label;

        SourceType(String icon, String label) {
            this.icon = icon;
            this.label = label;
        }

        public String getIcon() { return icon; }
        public String getLabel() { return label; }
    }

    private final SourceType source;
    private final String documentId;
    private final String documentName;
    private final String path;
    private final String snippet;
    private final float score;
    private final String chunkId;
    private final String heading;

    public SearchResult(SourceType source, String documentId, String documentName,
                        String path, String snippet, float score,
                        String chunkId, String heading) {
        this.source = source;
        this.documentId = documentId;
        this.documentName = documentName;
        this.path = path;
        this.snippet = snippet;
        this.score = score;
        this.chunkId = chunkId;
        this.heading = heading;
    }

    public SourceType getSource() { return source; }
    public String getDocumentId() { return documentId; }
    public String getDocumentName() { return documentName; }
    public String getPath() { return path; }
    public String getSnippet() { return snippet; }
    public float getScore() { return score; }
    public String getChunkId() { return chunkId; }
    public String getHeading() { return heading; }

    @Override
    public int compareTo(SearchResult other) {
        return Float.compare(other.score, this.score); // higher score first
    }

    @Override
    public String toString() {
        return source.getIcon() + " " + documentName + " (" + String.format("%.2f", score) + ")";
    }
}
