package de.bund.zrb.workflow.engine;

import de.zrb.bund.newApi.workflow.ResolutionContext;
import de.zrb.bund.newApi.workflow.ResolvableExpression;
import de.zrb.bund.newApi.workflow.UnresolvedSymbolException;

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

    @Override
    public Object resolve(ResolutionContext context) throws UnresolvedSymbolException {
        StringBuilder sb = new StringBuilder();
        for (ResolvableExpression part : parts) {
            if (!part.isResolved(context)) {
                throw new UnresolvedSymbolException("Composite contains unresolved part");
            }
            sb.append(part.resolve(context));
        }
        return sb.toString();
    }

    @Override
    public boolean isResolved(ResolutionContext context) {
        for (ResolvableExpression part : parts) {
            if (!part.isResolved(context)) return false;
        }
        return true;
    }
}
