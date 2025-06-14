package de.bund.zrb.workflow.engine;

import de.bund.zrb.util.StringUtil;
import de.zrb.bund.newApi.ResolvableExpression;

import java.util.*;
import java.util.stream.Collectors;

public class ExpressionTreeParser {

    private final Set<String> knownFunctions;

    public ExpressionTreeParser(Set<String> knownFunctions) {
        this.knownFunctions = new HashSet<>(knownFunctions);
    }

    public ResolvableExpression parse(String input) {
        input = input.trim();

        // Sonderfall: vollständiger Ausdruck in einem einzigen Paar {{...}}
        if (input.startsWith("{{") && input.endsWith("}}") && findMatchingEnd(input, 0) == input.length() - 2) {
            String inner = input.substring(2, input.length() - 2).trim();
            return parseSingle(inner); // ⬅ NICHT parse(inner)
        }

        List<ResolvableExpression> parts = new ArrayList<>();
        int index = 0;

        while (index < input.length()) {
            int start = input.indexOf("{{", index);
            if (start < 0) {
                parts.add(parseSingle(input.substring(index).trim()));
                break;
            }

            if (start > index) {
                parts.add(parseSingle(input.substring(index, start).trim()));
            }

            int end = findMatchingEnd(input, start);
            if (end < 0) {
                throw new IllegalArgumentException("Unmatched '{{' at position " + start);
            }

            String inner = input.substring(start + 2, end).trim();
            parts.add(parse(inner)); // ⬅ darf hier weiterhin parse sein

            index = end + 2;
        }

        if (parts.size() == 1) {
            return parts.get(0);
        }

        return new CompositeExpression(parts);
    }


    private ResolvableExpression parseSingle(String expr) {
        String unquoted = StringUtil.unquote(expr);
        if (!unquoted.equals(expr)) {
            return new LiteralExpression(unquoted);
        }

        int open = expr.indexOf('(');
        int close = expr.lastIndexOf(')');

        if (open > 0 && close > open && close == expr.length() - 1) {
            String funcName = expr.substring(0, open).trim();
            if (!knownFunctions.contains(funcName)) {
                throw new IllegalStateException("Unknown function: " + funcName);
            }

            String argsStr = expr.substring(open + 1, close);
            List<ResolvableExpression> args = parseArguments(argsStr);
            return new FunctionExpression(funcName, args);
        }

        return new VariableExpression(expr);
    }

    private int findMatchingEnd(String input, int start) {
        int depth = 0;
        for (int i = start; i < input.length() - 1; i++) {
            if (input.startsWith("{{", i)) {
                depth++;
                i++;
            } else if (input.startsWith("}}", i)) {
                depth--;
                if (depth == 0) {
                    return i;
                }
                i++;
            }
        }
        return -1;
    }

    private List<ResolvableExpression> parseArguments(String input) {
        List<ResolvableExpression> args = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        boolean inQuote = false;
        int braceDepth = 0;
        int parenDepth = 0;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (c == '\'') {
                inQuote = !inQuote;
                current.append(c);
                continue;
            }

            if (!inQuote) {
                if (input.startsWith("{{", i)) {
                    braceDepth++;
                    current.append("{{");
                    i++;
                    continue;
                } else if (input.startsWith("}}", i)) {
                    braceDepth--;
                    current.append("}}");
                    i++;
                    continue;
                } else if (c == '(') {
                    parenDepth++;
                } else if (c == ')') {
                    parenDepth--;
                } else if (c == ';' && braceDepth == 0 && parenDepth == 0) {
                    args.add(parse(current.toString().trim())); // <<< hier geändert
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
