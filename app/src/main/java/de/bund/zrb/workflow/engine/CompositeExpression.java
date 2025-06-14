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
        this.parts = new ArrayList<>(parts);
    }

    @Override
    public String resolve(VariableRegistry registry, ExpressionRegistry exprRegistry, long timeoutMillis) throws Exception {
        StringBuilder result = new StringBuilder();
        for (ResolvableExpression part : parts) {
            result.append(part.resolve(registry, exprRegistry, timeoutMillis));
        }
        return result.toString();
    }

    @Override
    public boolean isResolved(VariableRegistry registry) {
        for (ResolvableExpression part : parts) {
            if (!part.isResolved(registry)) {
                return false;
            }
        }
        return true;
    }

    public List<ResolvableExpression> getParts() {
        return new ArrayList<>(parts);
    }
}
