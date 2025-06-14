package de.bund.zrb.workflow.engine;

import de.zrb.bund.newApi.workflow.ResolutionContext;
import de.zrb.bund.newApi.workflow.ResolvableExpression;

public class LiteralExpression implements ResolvableExpression {

    private final Object value;

    public LiteralExpression(Object value) {
        this.value = value;
    }

    @Override
    public Object resolve(ResolutionContext context) {
        return value;
    }

    @Override
    public boolean isResolved(ResolutionContext context) {
        return true;
    }
}
