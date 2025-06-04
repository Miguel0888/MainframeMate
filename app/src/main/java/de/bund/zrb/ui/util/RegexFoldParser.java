package de.bund.zrb.ui.util;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.folding.Fold;
import org.fife.ui.rsyntaxtextarea.folding.FoldType;

import javax.swing.text.BadLocationException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public class RegexFoldParser implements org.fife.ui.rsyntaxtextarea.folding.FoldParser {

    private final Pattern pattern;
    private final Consumer<Boolean> matchResultCallback;

    public RegexFoldParser(String searchString, Consumer<Boolean> matchResultCallback) {
        if (searchString == null || searchString.trim().isEmpty()) {
            this.pattern = null;
        } else {
            // Alles in Großbuchstaben wandeln und case-insensitive Pattern erzeugen
            this.pattern = Pattern.compile(searchString.toUpperCase(), Pattern.CASE_INSENSITIVE);
        }

        this.matchResultCallback = matchResultCallback != null ? matchResultCallback : matched -> {};
    }

    @Override
    public List<Fold> getFolds(RSyntaxTextArea textArea) {
        List<Fold> folds = new ArrayList<>();

        if (pattern == null) {
            matchResultCallback.accept(false);
            return folds;
        }

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

            // Ergebnis an Callback melden
            matchResultCallback.accept(!matchLines.isEmpty());

            if (matchLines.isEmpty()) {
                return folds; // Nichts zu tun
            }

            // Schritt 2: zwischen sichtbaren Blöcken falten
            int lastVisibleLine = 0; // Erste Zeile immer sichtbar
            for (int matchLine : matchLines) {
                if (matchLine - lastVisibleLine > 1) {  // nur falten wenn es dazwischen mindestens eine Zeile zu verbergen gibt
                    addFold(textArea, folds, lastVisibleLine, matchLine-2);
                }
                lastVisibleLine = matchLine;
            }

            // Bereich nach letztem sichtbaren Match
            if (lastVisibleLine < lineCount - 1) {
                addFold(textArea, folds, lastVisibleLine, lineCount - 2);
            }

        } catch (BadLocationException e) {
            e.printStackTrace();
        }

        return folds;
    }

    private boolean matches(String line) {
        return pattern.matcher(line.toUpperCase()).find();
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
