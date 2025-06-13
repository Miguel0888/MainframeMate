package de.bund.zrb.workflow.engine;

import de.zrb.bund.newApi.ResolvableExpression;

import java.util.*;

public class ExpressionTreeParser {

    private final Set<String> knownFunctions;

    public ExpressionTreeParser(Set<String> knownFunctions) {
        this.knownFunctions = knownFunctions;
    }

    public ResolvableExpression parse(String input) {
        List<ResolvableExpression> parts = new ArrayList<>();

        int index = 0;
        while (index < input.length()) {
            int start = input.indexOf("{{", index);
            if (start < 0) {
                parts.add(new LiteralExpression(input.substring(index)));
                break;
            }

            if (start > index) {
                parts.add(new LiteralExpression(input.substring(index, start)));
            }

            int end = findMatchingBraces(input, start);
            if (end < 0) {
                throw new IllegalArgumentException("Unmatched '{{' at position " + start);
            }

            String inner = input.substring(start + 2, end);
            parts.add(parseInner(inner.trim()));
            index = end + 2;
        }

        if (parts.size() == 1) {
            return parts.get(0);
        }

        return new CompositeExpression(parts);
    }

    private int findMatchingBraces(String input, int start) {
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

    private ResolvableExpression parseInner(String expr) {
        expr = expr.trim();

        // String literal: enclosed in single quotes
        if (expr.startsWith("'") && expr.endsWith("'") && expr.length() >= 2) {
            return new LiteralExpression(expr.substring(1, expr.length() - 1));
        }

        int open = expr.indexOf('(');
        int close = expr.lastIndexOf(')');

        if (open > 0 && close > open && close == expr.length() - 1) {
            String funcName = expr.substring(0, open).trim();
            String argsString = expr.substring(open + 1, close).trim();

            if (!knownFunctions.contains(funcName)) {
                throw new IllegalStateException("Unknown function: " + funcName);
            }

            List<ResolvableExpression> args = parseArgs(argsString);
            return new FunctionExpression(funcName, args);
        }

        return new VariableExpression(expr);
    }

    private List<ResolvableExpression> parseArgs(String argsString) {
        List<ResolvableExpression> args = new ArrayList<>();

        StringBuilder current = new StringBuilder();
        boolean inQuote = false;
        int braceDepth = 0;
        int parenDepth = 0;

        for (int i = 0; i < argsString.length(); i++) {
            char c = argsString.charAt(i);

            if (c == '\'') {
                inQuote = !inQuote;
            } else if (!inQuote) {
                if (c == '(') parenDepth++;
                else if (c == ')') parenDepth--;
                else if (c == '{' && i + 1 < argsString.length() && argsString.charAt(i + 1) == '{') {
                    braceDepth++;
                    i++;
                    current.append("{{");
                    continue;
                } else if (c == '}' && i + 1 < argsString.length() && argsString.charAt(i + 1) == '}') {
                    braceDepth--;
                    i++;
                    current.append("}}");
                    continue;
                } else if (c == ';' && braceDepth == 0 && parenDepth == 0) {
                    args.add(parse(current.toString().trim()));
                    current.setLength(0);
                    continue;
                }
            }

            current.append(c);
        }

        if (current.length() > 0) {
            args.add(parse(current.toString().trim()));
        }

        return args;
    }
}
