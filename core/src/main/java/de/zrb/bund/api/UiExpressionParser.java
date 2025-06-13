package de.zrb.bund.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Parser für Eingaben in Mapping-Zellen: Literale, Excel-Spaltennamen oder Funktionsaufrufe.
 */
public final class UiExpressionParser {

    private UiExpressionParser() {
        // Hilfsklasse – keine Instanziierung erlaubt
    }

    public static Expression parse(String input, Set<String> knownFunctions) {
        String trimmed = input.trim();

        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
            return new Expression(ExpressionKind.LITERAL, trimmed.substring(1, trimmed.length() - 1), null, null);
        }

        int open = trimmed.indexOf('(');
        int close = trimmed.lastIndexOf(')');

        // Prüfen, ob es wie eine Funktion aussieht UND bekannt ist
        if (open > 0 && close > open) {
            String functionName = trimmed.substring(0, open).trim();
            if (knownFunctions.contains(functionName)) {
                String argString = trimmed.substring(open + 1, close).trim();
                List<String> args = argString.isEmpty() ? Collections.emptyList() : parseArgs(argString);
                return new Expression(ExpressionKind.FUNCTION, null, functionName, args);
            }
        }

        // Ansonsten: Ist einfach ein Spaltenname
        return new Expression(ExpressionKind.COLUMN, trimmed, null, null);
    }


    private static List<String> parseArgs(String input) {
        List<String> args = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean insideQuotes = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (c == '"' && (i == 0 || input.charAt(i - 1) != '\\')) {
                insideQuotes = !insideQuotes;
                current.append(c);
            } else if (c == ',' && !insideQuotes) {
                args.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }

        if (current.length() > 0) {
            args.add(current.toString().trim());
        }

        return args;
    }

    public enum ExpressionKind {
        LITERAL, COLUMN, FUNCTION
    }

    public static class Expression {
        private final ExpressionKind kind;
        private final String literalOrColumn;
        private final String functionName;
        private final List<String> arguments;

        public Expression(ExpressionKind kind, String literalOrColumn, String functionName, List<String> arguments) {
            this.kind = kind;
            this.literalOrColumn = literalOrColumn;
            this.functionName = functionName;
            this.arguments = arguments;
        }

        public ExpressionKind getKind() {
            return kind;
        }

        public String getLiteralOrColumn() {
            return literalOrColumn;
        }

        public String getFunctionName() {
            return functionName;
        }

        public List<String> getArguments() {
            return arguments;
        }

        @Override
        public String toString() {
            switch (kind) {
                case LITERAL:
                    return "\"" + literalOrColumn + "\"";
                case COLUMN:
                    return literalOrColumn;
                case FUNCTION:
                    return functionName + "(" + String.join(", ", arguments) + ")";
                default:
                    return "";
            }
        }
    }
}
