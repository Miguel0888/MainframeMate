package de.bund.zrb.ingestion.model.document;

/**
 * Base interface for all document blocks.
 * Blocks are immutable content elements within a Document.
 */
public interface Block {

    /**
     * Get the type of this block.
     */
    BlockType getType();
}

