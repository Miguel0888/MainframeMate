package de.bund.zrb.ingestion.model.document;

/**
 * A quote/blockquote block containing text.
 * Immutable.
 */
public final class QuoteBlock implements Block {

    private final String text;

    public QuoteBlock(String text) {
        this.text = text != null ? text : "";
    }

    @Override
    public BlockType getType() {
        return BlockType.QUOTE;
    }

    public String getText() {
        return text;
    }

    @Override
    public String toString() {
        return "QuoteBlock{text='" + (text.length() > 50 ? text.substring(0, 50) + "..." : text) + "'}";
    }
}

