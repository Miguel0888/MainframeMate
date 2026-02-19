package de.bund.zrb.ingestion.model.document;

/**
 * A code block with optional language and code content.
 * Immutable.
 */
public final class CodeBlock implements Block {

    private final String language;
    private final String code;

    public CodeBlock(String language, String code) {
        this.language = language != null ? language : "";
        this.code = code != null ? code : "";
    }

    public CodeBlock(String code) {
        this(null, code);
    }

    @Override
    public BlockType getType() {
        return BlockType.CODE;
    }

    public String getLanguage() {
        return language;
    }

    public String getCode() {
        return code;
    }

    public boolean hasLanguage() {
        return language != null && !language.isEmpty();
    }

    @Override
    public String toString() {
        return "CodeBlock{language='" + language + "', code=" + code.length() + " chars}";
    }
}

