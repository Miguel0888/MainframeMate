package de.bund.zrb.workflow.engine;

import de.zrb.bund.api.ExpressionRegistry;
import de.zrb.bund.newApi.ResolvableExpression;
import de.zrb.bund.newApi.VariableRegistry;

public class VariableExpression implements ResolvableExpression {
    private final String name;

    public VariableExpression(String name) {
        this.name = name;
    }

    public String resolve(VariableRegistry registry, ExpressionRegistry exprRegistry, long timeoutMillis) throws Exception {
        return registry.get(name, timeoutMillis);
    }

    public boolean isResolved(VariableRegistry registry) {
        return registry.has(name);
    }
}
