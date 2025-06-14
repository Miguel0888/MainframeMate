package de.bund.zrb.workflow.engine;

import de.zrb.bund.api.ExpressionRegistry;
import de.zrb.bund.newApi.ResolvableExpression;
import de.zrb.bund.newApi.VariableRegistry;

import java.util.ArrayList;
import java.util.List;

public class FunctionExpression implements ResolvableExpression {
    private final String functionName;
    private final List<ResolvableExpression> arguments;

    public FunctionExpression(String functionName, List<ResolvableExpression> arguments) {
        this.functionName = functionName;
        this.arguments = arguments;
    }

    public String getFunctionName() {
        return functionName;
    }

    public List<ResolvableExpression> getArguments() {
        return arguments;
    }
}


