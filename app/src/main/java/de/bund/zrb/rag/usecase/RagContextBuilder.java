package de.bund.zrb.rag.usecase;

import de.bund.zrb.rag.config.RagConfig;
import de.bund.zrb.rag.model.ScoredChunk;

import java.util.List;

/**
 * Builds hidden context from retrieved chunks.
 * Produces a deterministic format for injection into LLM prompts.
 */
public class RagContextBuilder {

    private final RagConfig config;

    public RagContextBuilder() {
        this(RagConfig.defaults());
    }

    public RagContextBuilder(RagConfig config) {
        this.config = config;
    }

    /**
     * Build hidden context from scored chunks.
     */
    public BuildResult build(List<ScoredChunk> chunks, String documentName) {
        if (chunks == null || chunks.isEmpty()) {
            return new BuildResult("", 0, 0, false);
        }

        StringBuilder context = new StringBuilder();
        context.append("--- RETRIEVED CONTEXT ---\n");
        context.append("Source: ").append(documentName != null ? documentName : "Attachments").append("\n");
        context.append("Relevant chunks: ").append(chunks.size()).append("\n\n");

        int totalChars = context.length();
        int includedChunks = 0;
        int truncatedChunks = 0;
        boolean wasLimited = false;

        for (int i = 0; i < chunks.size(); i++) {
            ScoredChunk sc = chunks.get(i);
            String chunkText = sc.getText();

            // Check per-chunk limit
            boolean chunkTruncated = false;
            if (chunkText.length() > config.getMaxContextCharsPerChunk()) {
                chunkText = truncateText(chunkText, config.getMaxContextCharsPerChunk());
                chunkTruncated = true;
                truncatedChunks++;
            }

            // Check total limit
            int chunkOverhead = 100; // Approximate header/footer size
            if (totalChars + chunkText.length() + chunkOverhead > config.getMaxContextCharsTotal()) {
                // Add a note about skipped chunks
                context.append("[").append(chunks.size() - i).append(" weitere Chunks ausgelassen wegen Kontextlimit]\n\n");
                wasLimited = true;
                break;
            }

            // Build chunk block
            context.append("### Chunk ").append(i + 1);
            context.append(" (score=").append(String.format("%.3f", sc.getScore()));
            context.append(", source=").append(sc.getSource());
            if (sc.getChunk().getHeading() != null) {
                context.append(", section=\"").append(sc.getChunk().getHeading()).append("\"");
            }
            if (chunkTruncated) {
                context.append(", truncated");
            }
            context.append(")\n");
            context.append(chunkText);
            if (!chunkText.endsWith("\n")) {
                context.append("\n");
            }
            context.append("\n");

            totalChars = context.length();
            includedChunks++;
        }

        context.append("--- END RETRIEVED CONTEXT ---");

        return new BuildResult(context.toString(), includedChunks, truncatedChunks, wasLimited);
    }

    /**
     * Build hidden context for multiple documents.
     */
    public BuildResult buildMultiDocument(List<DocumentChunks> documents) {
        if (documents == null || documents.isEmpty()) {
            return new BuildResult("", 0, 0, false);
        }

        StringBuilder context = new StringBuilder();
        context.append("--- RETRIEVED CONTEXT ---\n");
        context.append("Documents: ").append(documents.size()).append("\n\n");

        int totalChars = context.length();
        int totalIncluded = 0;
        int totalTruncated = 0;
        boolean wasLimited = false;

        for (DocumentChunks doc : documents) {
            if (totalChars > config.getMaxContextCharsTotal() * 0.9) {
                context.append("[Weitere Dokumente ausgelassen wegen Kontextlimit]\n");
                wasLimited = true;
                break;
            }

            context.append("## Document: ").append(doc.documentName).append("\n\n");

            for (int i = 0; i < doc.chunks.size(); i++) {
                ScoredChunk sc = doc.chunks.get(i);
                String chunkText = sc.getText();

                // Per-chunk truncation
                boolean chunkTruncated = false;
                if (chunkText.length() > config.getMaxContextCharsPerChunk()) {
                    chunkText = truncateText(chunkText, config.getMaxContextCharsPerChunk());
                    chunkTruncated = true;
                    totalTruncated++;
                }

                // Total limit check
                if (totalChars + chunkText.length() + 100 > config.getMaxContextCharsTotal()) {
                    context.append("[Weitere Chunks ausgelassen]\n\n");
                    wasLimited = true;
                    break;
                }

                context.append("### Chunk ").append(i + 1);
                context.append(" (score=").append(String.format("%.3f", sc.getScore())).append(")\n");
                context.append(chunkText);
                if (!chunkText.endsWith("\n")) {
                    context.append("\n");
                }
                context.append("\n");

                totalChars = context.length();
                totalIncluded++;
            }
        }

        context.append("--- END RETRIEVED CONTEXT ---");

        return new BuildResult(context.toString(), totalIncluded, totalTruncated, wasLimited);
    }

    private String truncateText(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }

        int cutPoint = maxLength - 30;

        // Try to find a sentence boundary
        int lastPeriod = text.lastIndexOf(". ", cutPoint);
        if (lastPeriod > cutPoint / 2) {
            return text.substring(0, lastPeriod + 1) + "\n[...gekürzt...]";
        }

        // Try to find a paragraph boundary
        int lastParagraph = text.lastIndexOf("\n\n", cutPoint);
        if (lastParagraph > cutPoint / 2) {
            return text.substring(0, lastParagraph) + "\n[...gekürzt...]";
        }

        // Hard cut
        return text.substring(0, cutPoint) + "\n[...gekürzt...]";
    }

    /**
     * Result of context building.
     */
    public static class BuildResult {
        private final String context;
        private final int includedChunks;
        private final int truncatedChunks;
        private final boolean wasLimited;

        public BuildResult(String context, int includedChunks, int truncatedChunks, boolean wasLimited) {
            this.context = context;
            this.includedChunks = includedChunks;
            this.truncatedChunks = truncatedChunks;
            this.wasLimited = wasLimited;
        }

        public String getContext() {
            return context;
        }

        public int getIncludedChunks() {
            return includedChunks;
        }

        public int getTruncatedChunks() {
            return truncatedChunks;
        }

        public boolean wasLimited() {
            return wasLimited;
        }

        public boolean isEmpty() {
            return context == null || context.isEmpty() || includedChunks == 0;
        }

        public boolean hasTruncations() {
            return truncatedChunks > 0 || wasLimited;
        }
    }

    /**
     * Chunks for a single document.
     */
    public static class DocumentChunks {
        public final String documentId;
        public final String documentName;
        public final List<ScoredChunk> chunks;

        public DocumentChunks(String documentId, String documentName, List<ScoredChunk> chunks) {
            this.documentId = documentId;
            this.documentName = documentName;
            this.chunks = chunks;
        }
    }
}

