package de.bund.zrb.workflow.engine;

import java.util.ArrayList;
import java.util.List;

public class Lexer {
    public List<Token> tokenize(String input) {
        List<Token> tokens = new ArrayList<>();
        int pos = 0;

        while (pos < input.length()) {
            int start = input.indexOf("{{", pos);
            if (start < 0) {
                tokens.add(new Token(Token.Type.TEXT, input.substring(pos)));
                break;
            }

            if (start > pos) {
                tokens.add(new Token(Token.Type.TEXT, input.substring(pos, start)));
            }

            int end = findMatchingEnd(input, start);
            if (end < 0) {
                throw new IllegalArgumentException("Unclosed '{{' at position " + start);
            }

            String exprContent = input.substring(start + 2, end).trim();
            tokens.add(new Token(Token.Type.EXPR, exprContent));
            pos = end + 2;
        }

        return tokens;
    }

    private int findMatchingEnd(String input, int start) {
        int depth = 0;
        for (int i = start; i < input.length() - 1; i++) {
            if (input.startsWith("{{", i)) {
                depth++;
                i++;
            } else if (input.startsWith("}}", i)) {
                depth--;
                if (depth == 0) return i;
                i++;
            }
        }
        return -1;
    }
}
