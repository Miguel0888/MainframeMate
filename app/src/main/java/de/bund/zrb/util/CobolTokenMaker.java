package de.bund.zrb.util;

import org.fife.ui.rsyntaxtextarea.AbstractTokenMaker;
import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rsyntaxtextarea.TokenMap;
import org.fife.ui.rsyntaxtextarea.TokenTypes;

import javax.swing.text.Segment;

public class CobolTokenMaker extends AbstractTokenMaker {

    @Override
    public TokenMap getWordsToHighlight() {
        TokenMap map = new TokenMap(true); // ignore case

        map.put("IDENTIFICATION", Token.RESERVED_WORD);
        map.put("DIVISION", Token.RESERVED_WORD);
        map.put("PROGRAM-ID", Token.RESERVED_WORD);
        map.put("ENVIRONMENT", Token.RESERVED_WORD);
        map.put("DATA", Token.RESERVED_WORD);
        map.put("WORKING-STORAGE", Token.RESERVED_WORD);
        map.put("PROCEDURE", Token.RESERVED_WORD);
        map.put("DISPLAY", Token.RESERVED_WORD);
        map.put("STOP", Token.RESERVED_WORD);
        map.put("RUN", Token.RESERVED_WORD);
        map.put("OPEN", Token.RESERVED_WORD);
        map.put("CLOSE", Token.RESERVED_WORD);
        map.put("READ", Token.RESERVED_WORD);
        map.put("PERFORM", Token.RESERVED_WORD);
        map.put("IF", Token.RESERVED_WORD);
        map.put("ELSE", Token.RESERVED_WORD);
        map.put("END-IF", Token.RESERVED_WORD);

        return map;
    }

    @Override
    public Token getTokenList(Segment text, int initialTokenType, int startOffset) {
        resetTokenList();

        int offset = startOffset;
        int currentTokenStart = text.offset;
        int currentTokenType = TokenTypes.NULL;

        int end = text.offset + text.count;
        TokenMap keywords = getWordsToHighlight();

        for (int i = text.offset; i < end; i++) {
            char c = text.array[i];

            switch (currentTokenType) {
                case TokenTypes.NULL:
                    currentTokenStart = i;
                    if (Character.isWhitespace(c)) {
                        currentTokenType = TokenTypes.WHITESPACE;
                    } else if (Character.isLetter(c)) {
                        currentTokenType = TokenTypes.IDENTIFIER;
                    } else {
                        currentTokenType = TokenTypes.IDENTIFIER;
                    }
                    break;

                case TokenTypes.WHITESPACE:
                    if (!Character.isWhitespace(c)) {
                        addToken(text, currentTokenStart, i - 1, TokenTypes.WHITESPACE, offset + currentTokenStart);
                        currentTokenStart = i;
                        currentTokenType = Character.isLetter(c) ? TokenTypes.IDENTIFIER : TokenTypes.IDENTIFIER;
                    }
                    break;

                case TokenTypes.IDENTIFIER:
                    if (!Character.isLetterOrDigit(c) && c != '-') {
                        int type = keywords.get(text.array, currentTokenStart, i - 1);
                        if (type == -1) {
                            type = TokenTypes.IDENTIFIER;
                        }
                        addToken(text, currentTokenStart, i - 1, type, offset + currentTokenStart);
                        currentTokenStart = i;
                        currentTokenType = Character.isWhitespace(c) ? TokenTypes.WHITESPACE : TokenTypes.IDENTIFIER;
                    }
                    break;
            }
        }

        // Letztes Token am Zeilenende
        if (currentTokenStart < end) {
            int type = TokenTypes.IDENTIFIER;
            if (currentTokenType == TokenTypes.IDENTIFIER) {
                int keywordType = keywords.get(text.array, currentTokenStart, end - 1);
                if (keywordType != -1) {
                    type = keywordType;
                }
            } else {
                type = currentTokenType;
            }
            addToken(text, currentTokenStart, end - 1, type, offset + currentTokenStart);
        }

        addNullToken();
        return firstToken;
    }
}
