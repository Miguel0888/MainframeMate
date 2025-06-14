package de.bund.zrb.workflow.engine;

import de.zrb.bund.newApi.ResolvableExpression;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static de.bund.zrb.util.StringUtil.unquote;

public class ExpressionParser {
    private final Set<String> knownFunctions;

    public ExpressionParser(Set<String> knownFunctions) {
        this.knownFunctions = knownFunctions;
    }

    public ResolvableExpression parseExpression(String expr) {
        expr = expr.trim();

        int parenIndex = expr.indexOf('(');
        if (parenIndex > 0 && expr.endsWith(")")) {
            String name = expr.substring(0, parenIndex).trim();
            if (!knownFunctions.contains(name)) {
                throw new IllegalArgumentException("Unknown function: " + name);
            }

            String argsString = expr.substring(parenIndex + 1, expr.length() - 1);
            List<ResolvableExpression> args = splitArgs(argsString);
            return new FunctionExpression(name, args);
        }

        return new VariableExpression(expr);

    }

    private List<ResolvableExpression> splitArgs(String input) {
        List<ResolvableExpression> args = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        boolean inQuote = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (c == '\'') {
                inQuote = !inQuote;
            } else if (!inQuote) {
                if (input.startsWith("{{", i)) {
                    depth++;
                    current.append("{{");
                    i++;
                    continue;
                } else if (input.startsWith("}}", i)) {
                    depth--;
                    current.append("}}");
                    i++;
                    continue;
                } else if (c == ';' && depth == 0) {
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

    public ResolvableExpression parse(String input) {
        Lexer lexer = new Lexer();
        List<Token> tokens = lexer.tokenize(input);

        if (tokens.size() == 1 && tokens.get(0).getType() == Token.Type.EXPR) {
            return parseExpression(tokens.get(0).getContent());
        }

        List<ResolvableExpression> parts = new ArrayList<>();
        for (Token token : tokens) {
            if (token.getType() == Token.Type.TEXT) {
                parts.add(new LiteralExpression(unquote(token.getContent())));
            } else {
                parts.add(parseExpression(token.getContent()));
            }
        }

        if (parts.size() == 1) {
            return parts.get(0);
        }

        return new CompositeExpression(parts);
    }
}
