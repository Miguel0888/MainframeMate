package de.bund.zrb.workflow.engine;

import de.zrb.bund.api.ExpressionRegistry;
import de.zrb.bund.newApi.workflow.ResolutionContext;
import de.zrb.bund.newApi.workflow.UnresolvedSymbolException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PollingResolutionContext implements ResolutionContext {

    private final Map<String, Object> symbolTable;

    private final ExpressionRegistry registry;

    public PollingResolutionContext(Map<String, Object> symbolTable, ExpressionRegistry registry) {
        this.symbolTable = symbolTable;
        this.registry = registry;
    }

    @Override
    public boolean isAvailable(String name) {
        // Check if symbol exists (no blocking)
        return symbolTable.containsKey(name) || registry.getKeys().contains(name);
    }

    @Override
    public Object resolve(String name) throws UnresolvedSymbolException {
        if (symbolTable.containsKey(name)) return symbolTable.get(name);
        throw new UnresolvedSymbolException(name);
    }

    @Override
    public Object invoke(String functionName, List<Object> args) {
        try {
            return registry.evaluate(functionName, args.stream().map(Object::toString).collect(Collectors.toList()));
        } catch (Exception e) {
            throw new RuntimeException("Fehler bei Funktionsausführung: " + functionName, e);
        }
    }

    public void provide(String name, Object value) {
        symbolTable.put(name, value);
    }
}
