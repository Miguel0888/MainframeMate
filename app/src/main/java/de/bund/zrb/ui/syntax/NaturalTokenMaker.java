package de.bund.zrb.ui.syntax;

import org.fife.ui.rsyntaxtextarea.AbstractTokenMaker;
import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rsyntaxtextarea.TokenMap;

import javax.swing.text.Segment;

/**
 * RSyntaxTextArea TokenMaker for Software AG Natural programming language.
 *
 * Highlights:
 *   - Keywords (DEFINE DATA, END-DEFINE, CALLNAT, READ, FIND, etc.)
 *   - Data types / formats (A, N, P, I, L, D, T, B, etc.)
 *   - Comments (lines starting with * or /*)
 *   - Strings (delimited by single quotes)
 *   - Numbers
 *   - System variables (*COUNTER, *ISN, *PF-KEY, etc.)
 *   - Hash/at/dollar-prefixed variables (#VAR, @VAR, $VAR)
 */
public class NaturalTokenMaker extends AbstractTokenMaker {

    // Token types reused from RSyntaxTextArea
    private static final int TT_NULL        = Token.NULL;
    private static final int TT_COMMENT     = Token.COMMENT_EOL;
    private static final int TT_KEYWORD     = Token.RESERVED_WORD;
    private static final int TT_KEYWORD2    = Token.RESERVED_WORD_2; // DB statements
    private static final int TT_DATA_TYPE   = Token.DATA_TYPE;
    private static final int TT_STRING      = Token.LITERAL_STRING_DOUBLE_QUOTE;
    private static final int TT_NUMBER      = Token.LITERAL_NUMBER_DECIMAL_INT;
    private static final int TT_VARIABLE    = Token.VARIABLE;
    private static final int TT_FUNCTION    = Token.FUNCTION;
    private static final int TT_SYSVAR      = Token.PREPROCESSOR; // *ISN, *COUNTER etc.
    private static final int TT_OPERATOR    = Token.OPERATOR;
    private static final int TT_SEPARATOR   = Token.SEPARATOR;
    private static final int TT_IDENTIFIER  = Token.IDENTIFIER;
    private static final int TT_WHITESPACE  = Token.WHITESPACE;

    private TokenMap keywords;

    public NaturalTokenMaker() {
        super();
    }

    @Override
    public TokenMap getWordsToHighlight() {
        if (keywords == null) {
            keywords = new TokenMap(true); // case-insensitive

            // ── Control flow ────────────────────────────────────────
            keywords.put("IF",               TT_KEYWORD);
            keywords.put("ELSE",             TT_KEYWORD);
            keywords.put("END-IF",           TT_KEYWORD);
            keywords.put("DECIDE",           TT_KEYWORD);
            keywords.put("ON",               TT_KEYWORD);
            keywords.put("FOR",              TT_KEYWORD);
            keywords.put("NONE",             TT_KEYWORD);
            keywords.put("ANY",              TT_KEYWORD);
            keywords.put("ALL",              TT_KEYWORD);
            keywords.put("VALUE",            TT_KEYWORD);
            keywords.put("VALUES",           TT_KEYWORD);
            keywords.put("END-DECIDE",       TT_KEYWORD);
            keywords.put("END-FOR",          TT_KEYWORD);
            keywords.put("REPEAT",           TT_KEYWORD);
            keywords.put("END-REPEAT",       TT_KEYWORD);
            keywords.put("UNTIL",            TT_KEYWORD);
            keywords.put("WHILE",            TT_KEYWORD);
            keywords.put("ESCAPE",           TT_KEYWORD);
            keywords.put("TOP",              TT_KEYWORD);
            keywords.put("BOTTOM",           TT_KEYWORD);
            keywords.put("ROUTINE",          TT_KEYWORD);
            keywords.put("LOOP",             TT_KEYWORD);

            // ── Data definition ─────────────────────────────────────
            keywords.put("DEFINE",           TT_KEYWORD);
            keywords.put("DATA",             TT_KEYWORD);
            keywords.put("END-DEFINE",       TT_KEYWORD);
            keywords.put("LOCAL",            TT_KEYWORD);
            keywords.put("PARAMETER",        TT_KEYWORD);
            keywords.put("GLOBAL",           TT_KEYWORD);
            keywords.put("INDEPENDENT",      TT_KEYWORD);
            keywords.put("USING",            TT_KEYWORD);
            keywords.put("CONST",            TT_KEYWORD);
            keywords.put("INIT",             TT_KEYWORD);
            keywords.put("VIEW",             TT_KEYWORD);
            keywords.put("OF",               TT_KEYWORD);
            keywords.put("REDEFINE",         TT_KEYWORD);
            keywords.put("END-REDEFINE",     TT_KEYWORD);
            keywords.put("FILLER",           TT_KEYWORD);

            // ── Subroutine / Procedure ──────────────────────────────
            keywords.put("SUBROUTINE",       TT_KEYWORD);
            keywords.put("END-SUBROUTINE",   TT_KEYWORD);
            keywords.put("PERFORM",          TT_KEYWORD);
            keywords.put("CALLNAT",          TT_KEYWORD);
            keywords.put("CALL",             TT_KEYWORD);
            keywords.put("FETCH",            TT_KEYWORD);
            keywords.put("RETURN",           TT_KEYWORD);
            keywords.put("END",              TT_KEYWORD);
            keywords.put("STOP",             TT_KEYWORD);
            keywords.put("TERMINATE",        TT_KEYWORD);

            // ── Database access (Adabas) ────────────────────────────
            keywords.put("READ",             TT_KEYWORD2);
            keywords.put("FIND",             TT_KEYWORD2);
            keywords.put("HISTOGRAM",        TT_KEYWORD2);
            keywords.put("STORE",            TT_KEYWORD2);
            keywords.put("UPDATE",           TT_KEYWORD2);
            keywords.put("DELETE",           TT_KEYWORD2);
            keywords.put("GET",              TT_KEYWORD2);
            keywords.put("END-READ",         TT_KEYWORD2);
            keywords.put("END-FIND",         TT_KEYWORD2);
            keywords.put("END-HISTOGRAM",    TT_KEYWORD2);
            keywords.put("WITH",             TT_KEYWORD2);
            keywords.put("WHERE",            TT_KEYWORD2);
            keywords.put("SORTED",           TT_KEYWORD2);
            keywords.put("DESCENDING",       TT_KEYWORD2);
            keywords.put("ASCENDING",        TT_KEYWORD2);
            keywords.put("BY",               TT_KEYWORD2);
            keywords.put("ISN",              TT_KEYWORD2);
            keywords.put("IN",               TT_KEYWORD2);
            keywords.put("PHYSICAL",         TT_KEYWORD2);
            keywords.put("LOGICAL",          TT_KEYWORD2);
            keywords.put("STARTING",         TT_KEYWORD2);
            keywords.put("FROM",             TT_KEYWORD2);
            keywords.put("ENDING",           TT_KEYWORD2);
            keywords.put("AT",               TT_KEYWORD2);
            keywords.put("WORK",             TT_KEYWORD2);
            keywords.put("FILE",             TT_KEYWORD2);
            keywords.put("RECORD",           TT_KEYWORD2);
            keywords.put("SAME",             TT_KEYWORD2);
            keywords.put("END-WORK",         TT_KEYWORD2);
            keywords.put("BACKOUT",          TT_KEYWORD2);
            keywords.put("TRANSACTION",      TT_KEYWORD2);
            keywords.put("END-TRANSACTION",  TT_KEYWORD2);

            // ── I/O ─────────────────────────────────────────────────
            keywords.put("INPUT",            TT_KEYWORD);
            keywords.put("WRITE",            TT_KEYWORD);
            keywords.put("DISPLAY",          TT_KEYWORD);
            keywords.put("PRINT",            TT_KEYWORD);
            keywords.put("MAP",              TT_KEYWORD);
            keywords.put("NEWPAGE",          TT_KEYWORD);
            keywords.put("EJECT",            TT_KEYWORD);
            keywords.put("FORMAT",           TT_KEYWORD);
            keywords.put("NOTITLE",          TT_KEYWORD);
            keywords.put("NOHDR",            TT_KEYWORD);

            // ── String/math operations ──────────────────────────────
            keywords.put("MOVE",             TT_KEYWORD);
            keywords.put("TO",               TT_KEYWORD);
            keywords.put("ASSIGN",           TT_KEYWORD);
            keywords.put("COMPUTE",          TT_KEYWORD);
            keywords.put("ADD",              TT_KEYWORD);
            keywords.put("SUBTRACT",         TT_KEYWORD);
            keywords.put("MULTIPLY",         TT_KEYWORD);
            keywords.put("DIVIDE",           TT_KEYWORD);
            keywords.put("GIVING",           TT_KEYWORD);
            keywords.put("REMAINDER",        TT_KEYWORD);
            keywords.put("ROUNDED",          TT_KEYWORD);
            keywords.put("COMPRESS",         TT_KEYWORD);
            keywords.put("EXAMINE",          TT_KEYWORD);
            keywords.put("REPLACE",          TT_KEYWORD);
            keywords.put("SEPARATE",         TT_KEYWORD);
            keywords.put("INTO",             TT_KEYWORD);
            keywords.put("SUBSTRING",        TT_KEYWORD);
            keywords.put("RESET",            TT_KEYWORD);

            // ── Comparison / logical ────────────────────────────────
            keywords.put("EQ",               TT_OPERATOR);
            keywords.put("NE",               TT_OPERATOR);
            keywords.put("LT",               TT_OPERATOR);
            keywords.put("LE",               TT_OPERATOR);
            keywords.put("GT",               TT_OPERATOR);
            keywords.put("GE",               TT_OPERATOR);
            keywords.put("AND",              TT_OPERATOR);
            keywords.put("OR",               TT_OPERATOR);
            keywords.put("NOT",              TT_OPERATOR);
            keywords.put("THRU",             TT_OPERATOR);
            keywords.put("TRUE",             TT_KEYWORD);
            keywords.put("FALSE",            TT_KEYWORD);
            keywords.put("EQUAL",            TT_OPERATOR);
            keywords.put("LESS",             TT_OPERATOR);
            keywords.put("THAN",             TT_OPERATOR);
            keywords.put("GREATER",          TT_OPERATOR);
            keywords.put("MASK",             TT_KEYWORD);
            keywords.put("SCAN",             TT_KEYWORD);
            keywords.put("MODIFIED",         TT_KEYWORD);
            keywords.put("SPECIFIED",        TT_KEYWORD);
            keywords.put("NUMERIC",          TT_KEYWORD);

            // ── Error handling ──────────────────────────────────────
            keywords.put("ON",               TT_KEYWORD);
            keywords.put("ERROR",            TT_KEYWORD);
            keywords.put("END-ERROR",        TT_KEYWORD);

            // ── Include / Copycode ──────────────────────────────────
            keywords.put("INCLUDE",          TT_KEYWORD);

            // ── Miscellaneous ───────────────────────────────────────
            keywords.put("IGNORE",           TT_KEYWORD);
            keywords.put("SUSPEND",          TT_KEYWORD);
            keywords.put("IDENTICAL",        TT_KEYWORD);
            keywords.put("SUPPRESS",         TT_KEYWORD);
            keywords.put("SET",              TT_KEYWORD);
            keywords.put("KEY",              TT_KEYWORD);
            keywords.put("NAMED",            TT_KEYWORD);
            keywords.put("STACK",            TT_KEYWORD);
            keywords.put("LIMIT",            TT_KEYWORD);
            keywords.put("NUMBER",           TT_KEYWORD);
        }
        return keywords;
    }

    @Override
    public void addToken(Segment segment, int start, int end, int tokenType, int startOffset) {
        if (tokenType == TT_IDENTIFIER) {
            int value = getWordsToHighlight().get(segment, start, end);
            if (value != -1) {
                tokenType = value;
            }
        }
        super.addToken(segment, start, end, tokenType, startOffset);
    }

    @Override
    public Token getTokenList(Segment text, int initialTokenType, int startOffset) {
        resetTokenList();

        char[] array = text.array;
        int offset = text.offset;
        int count = text.count;
        int end = offset + count;

        int newStartOffset = startOffset - offset;

        // Check for comment lines: lines starting with * or /*
        if (count >= 1) {
            // Skip leading whitespace for comment detection
            int firstNonSpace = offset;
            while (firstNonSpace < end && (array[firstNonSpace] == ' ' || array[firstNonSpace] == '\t')) {
                firstNonSpace++;
            }

            if (firstNonSpace < end) {
                // Check for ** comment
                if (array[firstNonSpace] == '*' && firstNonSpace + 1 < end && array[firstNonSpace + 1] == '*') {
                    // Whitespace before comment
                    if (firstNonSpace > offset) {
                        addToken(text, offset, firstNonSpace - 1, TT_WHITESPACE, newStartOffset + offset);
                    }
                    addToken(text, firstNonSpace, end - 1, TT_COMMENT, newStartOffset + firstNonSpace);
                    addNullToken();
                    return firstToken;
                }

                // Check for /* comment
                if (array[firstNonSpace] == '/' && firstNonSpace + 1 < end && array[firstNonSpace + 1] == '*') {
                    // Whitespace before comment
                    if (firstNonSpace > offset) {
                        addToken(text, offset, firstNonSpace - 1, TT_WHITESPACE, newStartOffset + offset);
                    }
                    addToken(text, firstNonSpace, end - 1, TT_COMMENT, newStartOffset + firstNonSpace);
                    addNullToken();
                    return firstToken;
                }
            }
        }

        int currentTokenStart = offset;
        int currentTokenType = TT_NULL;

        for (int i = offset; i < end; i++) {
            char c = array[i];

            switch (currentTokenType) {
                case TT_NULL:
                    currentTokenStart = i;
                    if (Character.isWhitespace(c)) {
                        currentTokenType = TT_WHITESPACE;
                    } else if (c == '\'') {
                        currentTokenType = TT_STRING;
                    } else if (c == '/' && i + 1 < end && array[i + 1] == '*') {
                        // Inline comment from here to end
                        if (currentTokenStart < i) {
                            // Should not happen as we're at TT_NULL
                        }
                        addToken(text, i, end - 1, TT_COMMENT, newStartOffset + i);
                        addNullToken();
                        return firstToken;
                    } else if (Character.isDigit(c)) {
                        currentTokenType = TT_NUMBER;
                    } else if (c == '*') {
                        // System variable like *ISN, *COUNTER
                        if (i + 1 < end && isNaturalIdentChar(array[i + 1])) {
                            currentTokenType = TT_SYSVAR;
                        } else {
                            addToken(text, i, i, TT_OPERATOR, newStartOffset + i);
                            currentTokenType = TT_NULL;
                        }
                    } else if (c == '#' || c == '@' || c == '$') {
                        // User variable: #VAR, @VAR, $VAR
                        currentTokenType = TT_VARIABLE;
                    } else if (c == '(' || c == ')' || c == ',') {
                        addToken(text, i, i, TT_SEPARATOR, newStartOffset + i);
                        currentTokenType = TT_NULL;
                    } else if (c == '=' || c == '+' || c == '-' || c == ':' || c == '<' || c == '>') {
                        addToken(text, i, i, TT_OPERATOR, newStartOffset + i);
                        currentTokenType = TT_NULL;
                    } else if (isNaturalIdentStartChar(c)) {
                        currentTokenType = TT_IDENTIFIER;
                    } else {
                        addToken(text, i, i, TT_IDENTIFIER, newStartOffset + i);
                        currentTokenType = TT_NULL;
                    }
                    break;

                case TT_WHITESPACE:
                    if (!Character.isWhitespace(c)) {
                        addToken(text, currentTokenStart, i - 1, TT_WHITESPACE, newStartOffset + currentTokenStart);
                        currentTokenType = TT_NULL;
                        i--; // re-process this char
                    }
                    break;

                case TT_STRING:
                    if (c == '\'') {
                        // Check for escaped quote ''
                        if (i + 1 < end && array[i + 1] == '\'') {
                            i++; // skip second quote
                        } else {
                            addToken(text, currentTokenStart, i, TT_STRING, newStartOffset + currentTokenStart);
                            currentTokenType = TT_NULL;
                        }
                    }
                    break;

                case TT_NUMBER:
                    if (!Character.isDigit(c) && c != '.') {
                        addToken(text, currentTokenStart, i - 1, TT_NUMBER, newStartOffset + currentTokenStart);
                        currentTokenType = TT_NULL;
                        i--;
                    }
                    break;

                case TT_SYSVAR:
                    if (!isNaturalIdentChar(c)) {
                        addToken(text, currentTokenStart, i - 1, TT_SYSVAR, newStartOffset + currentTokenStart);
                        currentTokenType = TT_NULL;
                        i--;
                    }
                    break;

                case TT_VARIABLE:
                    if (!isNaturalIdentChar(c)) {
                        addToken(text, currentTokenStart, i - 1, TT_VARIABLE, newStartOffset + currentTokenStart);
                        currentTokenType = TT_NULL;
                        i--;
                    }
                    break;

                case TT_IDENTIFIER:
                    if (!isNaturalIdentChar(c)) {
                        addToken(text, currentTokenStart, i - 1, TT_IDENTIFIER, newStartOffset + currentTokenStart);
                        currentTokenType = TT_NULL;
                        i--;
                    }
                    break;
            }
        }

        // Flush remaining token
        switch (currentTokenType) {
            case TT_NULL:
                addNullToken();
                break;
            default:
                addToken(text, currentTokenStart, end - 1, currentTokenType, newStartOffset + currentTokenStart);
                addNullToken();
                break;
        }

        return firstToken;
    }

    private boolean isNaturalIdentStartChar(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private boolean isNaturalIdentChar(char c) {
        return Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '.' || c == '#' || c == '@' || c == '$';
    }
}

