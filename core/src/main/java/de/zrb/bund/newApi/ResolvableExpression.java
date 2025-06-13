package de.zrb.bund.newApi;

import de.zrb.bund.api.ExpressionRegistry;

public interface ResolvableExpression {
    String resolve(VariableRegistry registry, ExpressionRegistry exprRegistry, long timeoutMillis) throws Exception;
    boolean isResolved(VariableRegistry registry);
}
