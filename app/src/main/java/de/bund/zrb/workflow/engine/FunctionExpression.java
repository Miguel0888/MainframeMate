package de.bund.zrb.workflow.engine;

import de.zrb.bund.api.ExpressionRegistry;
import de.zrb.bund.newApi.VariableRegistry;

import java.util.ArrayList;
import java.util.List;

public class FunctionExpression implements ResolvableExpression {
    private final String functionName;
    private final List<ResolvableExpression> args;

    public FunctionExpression(String functionName, List<ResolvableExpression> args) {
        this.functionName = functionName;
        this.args = args;
    }

    public String resolve(VariableRegistry registry, ExpressionRegistry exprRegistry, long timeoutMillis) throws Exception {
        List<String> resolvedArgs = new ArrayList<>();
        for (ResolvableExpression arg : args) {
            resolvedArgs.add(arg.resolve(registry, exprRegistry, timeoutMillis));
        }
        return exprRegistry.evaluate(functionName, resolvedArgs);
    }

    public boolean isResolved(VariableRegistry registry) {
        return args.stream().allMatch(a -> a.isResolved(registry));
    }
}

