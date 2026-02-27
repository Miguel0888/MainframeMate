package de.bund.zrb.search;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Shared highlighting utilities for search results.
 * Used by SearchTab (HTML-based) and ArchiveConnectionTab (JTextArea-based).
 */
public final class SearchHighlighter {

    private SearchHighlighter() {}

    /** Lucene boolean operators that should never be highlighted. */
    private static final Set<String> STOP_TERMS = new HashSet<String>(
            Arrays.asList("and", "or", "not"));

    /**
     * Highlight search terms in an HTML string with a yellow background.
     * Each query word (split by whitespace, min 2 chars) is wrapped in bold+yellow.
     * Lucene operators (AND, OR, NOT) are skipped.
     */
    public static String highlightHtml(String text, String query) {
        if (text == null || query == null || query.isEmpty()) return text != null ? text : "";
        String result = text;
        for (String term : query.toLowerCase().split("\\s+")) {
            if (term.length() < 2 || STOP_TERMS.contains(term)) continue;
            try {
                result = result.replaceAll("(?i)(" + Pattern.quote(term) + ")",
                        "<b style='background:#FFEB3B;color:#333;'>$1</b>");
            } catch (Exception ignored) {}
        }
        return result;
    }

    /**
     * Highlight all occurrences of the query in a JTextArea using the Swing Highlighter.
     * Case-insensitive. Each query word is highlighted separately.
     * Lucene operators (AND, OR, NOT) are skipped.
     */
    public static void highlightTextArea(JTextArea textArea, String query, Color color) {
        Highlighter highlighter = textArea.getHighlighter();
        highlighter.removeAllHighlights();

        if (query == null || query.isEmpty()) return;

        String text = textArea.getText().toLowerCase();
        Highlighter.HighlightPainter painter =
                new DefaultHighlighter.DefaultHighlightPainter(color);

        for (String term : query.toLowerCase().split("\\s+")) {
            if (term.length() < 2 || STOP_TERMS.contains(term)) continue;
            int idx = 0;
            while ((idx = text.indexOf(term, idx)) >= 0) {
                try {
                    highlighter.addHighlight(idx, idx + term.length(), painter);
                } catch (BadLocationException ignored) {}
                idx += term.length();
            }
        }
    }

    /**
     * Extract actual search terms from a query string, stripping Lucene operators.
     * E.g. "bundestag AND asyl" → ["bundestag", "asyl"]
     */
    public static List<String> extractSearchTerms(String query) {
        List<String> terms = new ArrayList<String>();
        if (query == null || query.isEmpty()) return terms;
        for (String word : query.split("\\s+")) {
            String clean = word.replaceAll("^[\"(]+|[\")+]+$", "").trim();
            if (clean.length() >= 2 && !STOP_TERMS.contains(clean.toLowerCase())) {
                terms.add(clean);
            }
        }
        return terms;
    }

    /** HTML-escape a string. */
    public static String escHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
