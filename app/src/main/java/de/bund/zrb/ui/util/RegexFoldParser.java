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

            // Schritt 1: Alle Zeilen mit Match merken
            for (int i = 0; i < lineCount; i++) {
                int startOffset = textArea.getLineStartOffset(i);
                int endOffset = textArea.getLineEndOffset(i);
                String line = textArea.getText(startOffset, endOffset - startOffset);
                if (matches(line)) {
                    matchLines.add(i);
                }
            }

            if (matchLines.isEmpty()) {
                return folds; // Nichts zu tun
            }

            // Schritt 2: zwischen sichtbaren Blöcken falten
            int lastVisibleLine = 0; // Erste Zeile immer sichtbar
            for (int matchLine : matchLines) {
                if (matchLine - lastVisibleLine > 1) {  // nur falten wenn es dazwischen mindestens eine Zeile zu verbergen gibt
                    addFold(textArea, folds, lastVisibleLine, matchLine-1);
                }
                lastVisibleLine = matchLine;
            }

            // Bereich nach letztem sichtbaren Match
            if (lastVisibleLine < lineCount - 1) {
                addFold(textArea, folds, lastVisibleLine, lineCount - 1);
            }

        } catch (BadLocationException e) {
            e.printStackTrace();
        }

        return folds;
    }

    private boolean matches(String line) {
        return !searchString.isEmpty() && line.contains(searchString);
    }

    private void addFold(RSyntaxTextArea textArea, List<Fold> folds, int startLine, int endLine) throws BadLocationException {
        if (startLine > endLine) return;

        int startOffset = textArea.getLineStartOffset(startLine);
        int endOffset = textArea.getLineEndOffset(endLine-1);

        Fold fold = new Fold(FoldType.CODE, textArea, startOffset);
        fold.setEndOffset(endOffset);
        fold.setCollapsed(true);
        folds.add(fold);
    }
}
