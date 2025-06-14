package de.bund.zrb.workflow.engine;

import de.zrb.bund.newApi.workflow.ResolvableExpression;

import java.util.Set;

public class ExpressionTreeParser {
    private final ExpressionParser parser;

    public ExpressionTreeParser(Set<String> knownFunctions) {
        this.parser = new ExpressionParser(knownFunctions);
    }

    public ResolvableExpression parse(String input) {
        return parser.parse(input);
    }
}
