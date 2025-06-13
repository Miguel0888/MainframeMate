package de.bund.zrb.workflow.engine;

import de.zrb.bund.api.ExpressionRegistry;
import de.zrb.bund.newApi.VariableRegistry;

interface ResolvableExpression {
    String resolve(VariableRegistry registry, ExpressionRegistry exprRegistry, long timeoutMillis) throws Exception;
    boolean isResolved(VariableRegistry registry);
}
