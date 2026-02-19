package de.bund.zrb.ingestion.model.document;

/**
 * A paragraph block containing text.
 * Immutable.
 */
public final class ParagraphBlock implements Block {

    private final String text;

    public ParagraphBlock(String text) {
        this.text = text != null ? text : "";
    }

    @Override
    public BlockType getType() {
        return BlockType.PARAGRAPH;
    }

    public String getText() {
        return text;
    }

    @Override
    public String toString() {
        return "ParagraphBlock{text='" + (text.length() > 50 ? text.substring(0, 50) + "..." : text) + "'}";
    }
}

