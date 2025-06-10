package de.zrb.bund.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Hilfsklasse zum Parsen dynamischer Funktionsaufrufe wie:
 * DDMMJJ, FormatDate("2024-01-01"), ToUpper(Name)
 */
public final class ExpressionCallParser {

    private ExpressionCallParser() {
        // Verhindere Instanziierung
    }

    public static ExpressionCall parse(String input) {
        String trimmed = input.trim();

        int open = trimmed.indexOf('(');
        int close = trimmed.lastIndexOf(')');

        if (open == -1 || close == -1 || close < open) {
            // Kein Klammerausdruck – parameterlose Funktion
            return new ExpressionCall(trimmed, Collections.emptyList());
        }

        String functionName = trimmed.substring(0, open).trim();
        String argString = trimmed.substring(open + 1, close).trim();

        List<String> rawArgs = argString.isEmpty() ? Collections.emptyList() : parseArgs(argString);
        return new ExpressionCall(functionName, rawArgs);
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

    /**
     * Ergebnis eines Funktionsparsing-Vorgangs.
     */
    public static class ExpressionCall {
        private final String functionName;
        private final List<String> rawArgs;

        public ExpressionCall(String functionName, List<String> rawArgs) {
            this.functionName = functionName;
            this.rawArgs = rawArgs;
        }

        public String getFunctionName() {
            return functionName;
        }

        public List<String> getRawArgs() {
            return rawArgs;
        }

        public boolean isLiteral(String arg) {
            return arg.startsWith("\"") && arg.endsWith("\"");
        }

        public String stripQuotes(String arg) {
            return isLiteral(arg) ? arg.substring(1, arg.length() - 1) : arg;
        }

        @Override
        public String toString() {
            return functionName + "(" + String.join(", ", rawArgs) + ")";
        }
    }
}
