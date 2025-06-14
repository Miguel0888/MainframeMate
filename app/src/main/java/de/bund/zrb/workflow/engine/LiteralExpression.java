package de.bund.zrb.workflow.engine;

import de.zrb.bund.newApi.workflow.ResolutionContext;
import de.zrb.bund.newApi.workflow.ResolvableExpression;

public class LiteralExpression implements ResolvableExpression {
    private final String value;

    public LiteralExpression(String value) {
        this.value = value;
    }

    public Object resolve() {
        return resolve(null);
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
