package de.bund.zrb.workflow.engine;

import de.zrb.bund.api.ExpressionRegistry;
import de.zrb.bund.newApi.ResolvableExpression;
import de.zrb.bund.newApi.VariableRegistry;

import java.util.List;

/**
 *  CompositeExpression, die mehrere ResolvableExpression-Elemente sequentiell zusammenfügt – typisch für
 *  Misch-Ausdrücke wie "Benutzer {{user}} hat {{count}} Nachrichten":
 */
public class CompositeExpression implements ResolvableExpression {

    private final List<ResolvableExpression> parts;

    public CompositeExpression(List<ResolvableExpression> parts) {
        this.parts = parts;
    }

    @Override
    public String resolve(VariableRegistry registry, ExpressionRegistry exprRegistry, long timeoutMillis) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (ResolvableExpression part : parts) {
            sb.append(part.resolve(registry, exprRegistry, timeoutMillis));
        }
        return sb.toString();
    }

    @Override
    public boolean isResolved(VariableRegistry registry) {
        for (ResolvableExpression part : parts) {
            if (!part.isResolved(registry)) {
                return false;
            }
        }
        return true;
    }

    public List<ResolvableExpression> getParts() {
        return parts;
    }
}
