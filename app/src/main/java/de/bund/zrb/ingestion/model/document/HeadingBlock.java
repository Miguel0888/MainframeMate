package de.bund.zrb.ingestion.model.document;

/**
 * A heading block with level (1-6) and text.
 * Immutable.
 */
public final class HeadingBlock implements Block {

    private final int level;
    private final String text;

    public HeadingBlock(int level, String text) {
        if (level < 1 || level > 6) {
            throw new IllegalArgumentException("Heading level must be between 1 and 6");
        }
        this.level = level;
        this.text = text != null ? text : "";
    }

    @Override
    public BlockType getType() {
        return BlockType.HEADING;
    }

    public int getLevel() {
        return level;
    }

    public String getText() {
        return text;
    }

    @Override
    public String toString() {
        return "HeadingBlock{level=" + level + ", text='" + text + "'}";
    }
}

