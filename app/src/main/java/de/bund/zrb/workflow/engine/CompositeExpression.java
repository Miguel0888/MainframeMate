package de.bund.zrb.workflow.engine;

import de.zrb.bund.api.ExpressionRegistry;
import de.zrb.bund.newApi.ResolvableExpression;
import de.zrb.bund.newApi.VariableRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves a list of subexpressions and concatenates their results.
 */
public class CompositeExpression implements ResolvableExpression {
    private final List<ResolvableExpression> parts;

    public CompositeExpression(List<ResolvableExpression> parts) {
        this.parts = parts;
    }

    public List<ResolvableExpression> getParts() {
        return parts;
    }
}
