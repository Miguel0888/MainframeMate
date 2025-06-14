package de.zrb.bund.newApi.workflow;

public interface ResolvableExpression {
    Object resolve(ResolutionContext context) throws UnresolvedSymbolException;
    boolean isResolved(ResolutionContext context);
}
