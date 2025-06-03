package de.bund.zrb.ui.util;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.folding.Fold;
import org.fife.ui.rsyntaxtextarea.folding.FoldType;

import javax.swing.text.BadLocationException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class RegexFoldParser implements org.fife.ui.rsyntaxtextarea.folding.FoldParser {

    private final Pattern pattern;

    public RegexFoldParser(String regex) {
        this.pattern = Pattern.compile(regex);
    }

    @Override
    public List<Fold> getFolds(RSyntaxTextArea textArea) {
        List<Fold> folds = new ArrayList<>();

        Fold currentFold = null;

        try {
            int lineCount = textArea.getLineCount();
            for (int i = 0; i < lineCount; i++) {
                int startOffset = textArea.getLineStartOffset(i);
                int endOffset = textArea.getLineEndOffset(i);
                String line = textArea.getText(startOffset, endOffset - startOffset).trim();
                boolean matches = pattern.matcher(line).matches();

                if (!matches) {
                    if (currentFold == null) {
                        currentFold = new Fold(FoldType.CODE, textArea, startOffset);
                    }
                    currentFold.setEndOffset(endOffset);
                    currentFold.setCollapsed(true);
                } else {
                    if (currentFold != null) {
                        folds.add(currentFold);
                        currentFold = null;
                    }
                }
            }
            if (currentFold != null) {
                folds.add(currentFold);
            }

        } catch (BadLocationException e) {
            e.printStackTrace();
        }

        return folds;
    }

}
