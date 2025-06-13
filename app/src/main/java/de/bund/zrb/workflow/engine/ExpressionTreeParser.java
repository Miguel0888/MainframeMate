package de.bund.zrb.workflow.engine;

import de.zrb.bund.newApi.ResolvableExpression;

import java.util.*;

public class ExpressionTreeParser {

    private final Set<String> knownFunctions;

    public ExpressionTreeParser(Set<String> knownFunctions) {
        this.knownFunctions = knownFunctions;
    }

    public ResolvableExpression parse(String input) {
        return parseRecursive(stripBraces(input.trim()));
    }

    private ResolvableExpression parseRecursive(String expr) {
        if (expr == null || expr.isEmpty()) {
            return new LiteralExpression("");
        }

        // Sonderfall: einfache Variable {{name}}
        if (!expr.contains("(") && !expr.contains(",")) {
            return new VariableExpression(expr.trim());
        }

        int openParen = expr.indexOf('(');
        int closeParen = expr.lastIndexOf(')');

        if (openParen > 0 && closeParen > openParen && closeParen == expr.length() - 1) {
            String funcName = expr.substring(0, openParen).trim();
            String argsString = expr.substring(openParen + 1, closeParen).trim();

            if (!knownFunctions.contains(funcName)) {
                return new LiteralExpression(expr);
            }

            List<ResolvableExpression> args = parseArgs(argsString);
            return new FunctionExpression(funcName, args);
        }

        return new LiteralExpression(expr);
    }

    private List<ResolvableExpression> parseArgs(String argsString) {
        List<ResolvableExpression> args = new ArrayList<>();

        int depth = 0;
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < argsString.length(); i++) {
            char c = argsString.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == ',' && depth == 0) {
                args.add(parse(stripBraces(current.toString().trim())));
                current.setLength(0);
                continue;
            }
            current.append(c);
        }

        if (current.length() > 0) {
            args.add(parse(stripBraces(current.toString().trim())));
        }

        return args;
    }

    private String stripBraces(String text) {
        if (text.startsWith("{{") && text.endsWith("}}")) {
            return text.substring(2, text.length() - 2);
        }
        return text;
    }
}
