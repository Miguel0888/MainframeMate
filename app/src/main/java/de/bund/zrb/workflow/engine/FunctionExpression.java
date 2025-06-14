package de.bund.zrb.workflow.engine;

import de.zrb.bund.newApi.workflow.ResolutionContext;
import de.zrb.bund.newApi.workflow.ResolvableExpression;
import de.zrb.bund.newApi.workflow.UnresolvedSymbolException;

import java.util.ArrayList;
import java.util.List;

public class FunctionExpression implements ResolvableExpression {
    private final String functionName;
    private final List<ResolvableExpression> arguments;

    public FunctionExpression(String functionName, List<ResolvableExpression> arguments) {
        this.functionName = functionName;
        this.arguments = arguments;
    }

    @Override
    public Object resolve(ResolutionContext context) throws UnresolvedSymbolException {
        if (!context.isAvailable(functionName)) {
            throw new UnresolvedSymbolException(functionName);
        }

        List<Object> resolvedArgs = new ArrayList<>();
        for (ResolvableExpression arg : arguments) {
            if (!arg.isResolved(context)) {
                throw new UnresolvedSymbolException("Argument unresolved for function: " + functionName);
            }
            resolvedArgs.add(arg.resolve(context));
        }

        return context.invoke(functionName, resolvedArgs);
    }

    @Override
    public boolean isResolved(ResolutionContext context) {
        if (!context.isAvailable(functionName)) return false;
        for (ResolvableExpression arg : arguments) {
            if (!arg.isResolved(context)) return false;
        }
        return true;
    }
}


