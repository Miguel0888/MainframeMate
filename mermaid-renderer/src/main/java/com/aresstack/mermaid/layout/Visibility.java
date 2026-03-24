package com.aresstack.mermaid.layout;

/**
 * UML visibility modifier for class members.
 *
 * <h3>Mermaid syntax mapping</h3>
 * <pre>
 *   PUBLIC     →  +   (e.g. +name: String)
 *   PRIVATE    →  -   (e.g. -id: int)
 *   PROTECTED  →  #   (e.g. #helper(): void)
 *   PACKAGE    →  ~   (e.g. ~internal: boolean)
 * </pre>
 */
public enum Visibility {

    /** {@code +} public */
    PUBLIC("+", "public"),

    /** {@code -} private */
    PRIVATE("-", "private"),

    /** {@code #} protected */
    PROTECTED("#", "protected"),

    /** {@code ~} package-private / internal */
    PACKAGE("~", "package");

    private final String symbol;
    private final String displayName;

    Visibility(String symbol, String displayName) {
        this.symbol = symbol;
        this.displayName = displayName;
    }

    /** The single-character prefix used in Mermaid class definitions. */
    public String getSymbol() { return symbol; }

    /** Java-style keyword for display. */
    public String getDisplayName() { return displayName; }

    /**
     * Parse visibility from a leading character.
     *
     * @param ch the first character of a member declaration
     * @return the matching visibility, or {@code null} if no match
     */
    public static Visibility fromChar(char ch) {
        switch (ch) {
            case '+': return PUBLIC;
            case '-': return PRIVATE;
            case '#': return PROTECTED;
            case '~': return PACKAGE;
            default:  return null;
        }
    }

    @Override
    public String toString() { return symbol; }
}

