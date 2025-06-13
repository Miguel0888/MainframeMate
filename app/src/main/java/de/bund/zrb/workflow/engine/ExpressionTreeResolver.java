package de.bund.zrb.workflow.engine;

import de.zrb.bund.api.ExpressionRegistry;
import de.zrb.bund.newApi.ResolvableExpression;
import de.zrb.bund.newApi.VariableRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

public class ExpressionTreeResolver {

    private final VariableRegistry variableRegistry;
    private final ExpressionRegistry expressionRegistry;
    private final long timeoutMillis;

    public ExpressionTreeResolver(VariableRegistry variableRegistry,
                                  ExpressionRegistry expressionRegistry,
                                  long timeoutMillis) {
        this.variableRegistry = variableRegistry;
        this.expressionRegistry = expressionRegistry;
        this.timeoutMillis = timeoutMillis;
    }

    public String resolve(ResolvableExpression expr) throws Exception {
        if (expr instanceof LiteralExpression) {
            return ((LiteralExpression) expr).getValue();

        } else if (expr instanceof VariableExpression) {
            String key = ((VariableExpression) expr).getName();

            if (!variableRegistry.contains(key)) {
                throw new IllegalStateException("Variable not set: " + key);
            }

            String value = variableRegistry.get(key, timeoutMillis);
            if (value == null) {
                throw new IllegalStateException("Variable \"" + key + "\" resolved to null.");
            }

            return value;

        } else if (expr instanceof FunctionExpression) {
            FunctionExpression fExpr = (FunctionExpression) expr;

            if (!expressionRegistry.getFunctionNames().contains(fExpr.getFunctionName())) {
                throw new IllegalStateException("Unknown function: " + fExpr.getFunctionName());
            }

            List<String> argValues = new ArrayList<>();
            for (ResolvableExpression arg : fExpr.getArguments()) {
                argValues.add(resolve(arg));
            }

            return expressionRegistry.evaluate(fExpr.getFunctionName(), argValues);

        } else {
            throw new IllegalStateException("Unsupported expression type: " + expr.getClass().getSimpleName());
        }
    }
}
