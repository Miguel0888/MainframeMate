package de.bund.zrb.ui.util;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.folding.Fold;
import org.fife.ui.rsyntaxtextarea.folding.FoldType;

import javax.swing.text.BadLocationException;
import java.util.ArrayList;
import java.util.List;

public class RegexFoldParser implements org.fife.ui.rsyntaxtextarea.folding.FoldParser {

    private final String searchString;

    public RegexFoldParser(String searchString) {
        this.searchString = searchString != null ? searchString.trim() : "";
    }

    @Override
    public List<Fold> getFolds(RSyntaxTextArea textArea) {
        List<Fold> folds = new ArrayList<>();

        try {
            int lineCount = textArea.getLineCount();
            List<Integer> matchLines = new ArrayList<>();

            // Schritt 1: Zeilen mit Match sammeln
            for (int i = 0; i < lineCount; i++) {
                int startOffset = textArea.getLineStartOffset(i);
                int endOffset = textArea.getLineEndOffset(i);
                String line = textArea.getText(startOffset, endOffset - startOffset);

                if (matches(line)) {
                    matchLines.add(i);
                }
            }

            if (matchLines.isEmpty()) {
                return folds; // später: alles einfärben statt folden
            }

            // Schritt 2: zwischen Nicht-Trefferblöcke falten
            int lastMatchLine = -1;
            for (int i = 0; i < matchLines.size(); i++) {
                int matchLine = matchLines.get(i);

                // vor erstem Treffer
                if (i == 0 && matchLine > 0) {
                    addFold(textArea, folds, 0, matchLine - 1);
                }

                // zwischen zwei Treffern
                if (lastMatchLine != -1 && matchLine - lastMatchLine > 1) {
                    addFold(textArea, folds, lastMatchLine + 1, matchLine - 1);
                }

                lastMatchLine = matchLine;
            }

            // nach letztem Treffer
            if (lastMatchLine < lineCount - 1) {
                addFold(textArea, folds, lastMatchLine + 1, lineCount - 1);
            }

        } catch (BadLocationException e) {
            e.printStackTrace();
        }

        return folds;
    }

    /**
     * Prüft, ob eine Zeile als Treffer gewertet wird.
     */
    private boolean matches(String line) {
        boolean result = !searchString.isEmpty() && line.contains(searchString);
        return result;
    }

    private void addFold(RSyntaxTextArea textArea, List<Fold> folds, int startLine, int endLine) throws BadLocationException {
        if (startLine > endLine) return;

        int startOffset = textArea.getLineStartOffset(startLine);
        int endOffset = textArea.getLineEndOffset(endLine);

        Fold fold = new Fold(FoldType.CODE, textArea, startOffset);
        fold.setEndOffset(endOffset);
        fold.setCollapsed(true);
        folds.add(fold);
    }
}
