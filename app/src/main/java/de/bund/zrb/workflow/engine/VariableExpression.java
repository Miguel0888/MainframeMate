package de.bund.zrb.workflow.engine;

import de.zrb.bund.newApi.workflow.ResolutionContext;
import de.zrb.bund.newApi.workflow.ResolvableExpression;
import de.zrb.bund.newApi.workflow.UnresolvedSymbolException;
import org.jetbrains.annotations.NotNull;

public class VariableExpression implements ResolvableExpression {
    private final String name;

    public VariableExpression(String name) {
        this.name = name;
    }

    @Override
    public Object resolve(@NotNull ResolutionContext context) throws UnresolvedSymbolException {
        if (!context.isAvailable(name)) {
            throw new UnresolvedSymbolException(name);
        }
        return context.resolve(name);
    }

    @Override
    public boolean isResolved(@NotNull ResolutionContext context) {
        return context.isAvailable(name);
    }
}
