package de.bund.zrb.ingestion.model.document;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A Document consisting of an ordered list of Blocks and optional metadata.
 * Immutable.
 */
public final class Document {

    private final List<Block> blocks;
    private final DocumentMetadata metadata;

    private Document(List<Block> blocks, DocumentMetadata metadata) {
        this.blocks = blocks != null
            ? Collections.unmodifiableList(new ArrayList<>(blocks))
            : Collections.<Block>emptyList();
        this.metadata = metadata;
    }

    /**
     * Get all blocks in this document.
     */
    public List<Block> getBlocks() {
        return blocks;
    }

    /**
     * Get the document metadata (may be null).
     */
    public DocumentMetadata getMetadata() {
        return metadata;
    }

    /**
     * Check if this document has any blocks.
     */
    public boolean isEmpty() {
        return blocks.isEmpty();
    }

    /**
     * Get the number of blocks.
     */
    public int getBlockCount() {
        return blocks.size();
    }

    /**
     * Create a new Document builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create a simple document with a single paragraph.
     */
    public static Document fromText(String text) {
        return builder()
                .addBlock(new ParagraphBlock(text))
                .build();
    }

    /**
     * Create a simple document with a single paragraph and metadata.
     */
    public static Document fromText(String text, DocumentMetadata metadata) {
        return builder()
                .metadata(metadata)
                .addBlock(new ParagraphBlock(text))
                .build();
    }

    @Override
    public String toString() {
        return "Document{blocks=" + blocks.size() +
               ", metadata=" + (metadata != null ? metadata.getSourceName() : "null") + "}";
    }

    public static class Builder {
        private List<Block> blocks = new ArrayList<>();
        private DocumentMetadata metadata;

        public Builder addBlock(Block block) {
            if (block != null) {
                blocks.add(block);
            }
            return this;
        }

        public Builder addBlocks(List<Block> blocks) {
            if (blocks != null) {
                this.blocks.addAll(blocks);
            }
            return this;
        }

        public Builder heading(int level, String text) {
            return addBlock(new HeadingBlock(level, text));
        }

        public Builder paragraph(String text) {
            return addBlock(new ParagraphBlock(text));
        }

        public Builder code(String language, String code) {
            return addBlock(new CodeBlock(language, code));
        }

        public Builder code(String code) {
            return addBlock(new CodeBlock(code));
        }

        public Builder quote(String text) {
            return addBlock(new QuoteBlock(text));
        }

        public Builder list(boolean ordered, List<String> items) {
            return addBlock(new ListBlock(ordered, items));
        }

        public Builder table(List<String> headers, List<List<String>> rows) {
            return addBlock(new TableBlock(headers, rows));
        }

        public Builder metadata(DocumentMetadata metadata) {
            this.metadata = metadata;
            return this;
        }

        public Document build() {
            return new Document(blocks, metadata);
        }
    }
}

