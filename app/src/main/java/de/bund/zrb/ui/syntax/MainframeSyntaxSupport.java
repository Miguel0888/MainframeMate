package de.bund.zrb.ui.syntax;

import org.fife.ui.rsyntaxtextarea.AbstractTokenMakerFactory;
import org.fife.ui.rsyntaxtextarea.TokenMakerFactory;

/**
 * Registers custom Mainframe syntax highlighters (Natural, JCL, COBOL)
 * with RSyntaxTextArea's TokenMakerFactory.
 *
 * Call {@link #register()} once at application startup.
 *
 * Custom MIME types:
 *   - {@code text/natural}  → Natural (Software AG)
 *
 * Usage in RSyntaxTextArea:
 *   textArea.setSyntaxEditingStyle("text/natural");
 */
public final class MainframeSyntaxSupport {

    /** MIME type for Natural syntax highlighting */
    public static final String SYNTAX_STYLE_NATURAL = "text/natural";

    private static boolean registered = false;

    private MainframeSyntaxSupport() { /* utility */ }

    /**
     * Register all custom Mainframe TokenMakers.
     * Safe to call multiple times – registration happens only once.
     */
    public static synchronized void register() {
        if (registered) return;

        TokenMakerFactory factory = TokenMakerFactory.getDefaultInstance();
        if (factory instanceof AbstractTokenMakerFactory) {
            AbstractTokenMakerFactory atmf = (AbstractTokenMakerFactory) factory;

            atmf.putMapping(SYNTAX_STYLE_NATURAL,
                    "de.bund.zrb.ui.syntax.NaturalTokenMaker");

            registered = true;
        }
    }
}

