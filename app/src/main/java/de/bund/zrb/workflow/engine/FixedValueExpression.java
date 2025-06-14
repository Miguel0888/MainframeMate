package de.bund.zrb.workflow.engine;

import de.zrb.bund.newApi.workflow.ResolutionContext;
import de.zrb.bund.newApi.workflow.ResolvableExpression;

/**
 * Contains a unquoted Literal. Used for JSON Numbers and Booleans.
 * This Class is just a dummy for JSON serialization and not used by the parser!
 * ToDo: Maybe combined with `LiteralExpression` in future.
 */
public class FixedValueExpression implements ResolvableExpression {
    private final Object value;

    public FixedValueExpression(Object value) {
        this.value = value;
    }

    @Override
    public Object resolve(ResolutionContext ctx) {
        return value;
    }

    @Override
    public boolean isResolved(ResolutionContext context) {
        return true;
    }
}
