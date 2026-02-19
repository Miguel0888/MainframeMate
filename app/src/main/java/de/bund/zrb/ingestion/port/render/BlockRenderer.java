package de.bund.zrb.ingestion.port.render;

import de.bund.zrb.ingestion.model.document.Block;

/**
 * Port interface for rendering a single Block.
 */
public interface BlockRenderer {

    /**
     * Check if this renderer supports the given block.
     *
     * @param block the block to check
     * @return true if this renderer can render the block
     */
    boolean supports(Block block);

    /**
     * Render the block to the output.
     *
     * @param block the block to render
     * @param out the output StringBuilder
     */
    void render(Block block, StringBuilder out);
}

