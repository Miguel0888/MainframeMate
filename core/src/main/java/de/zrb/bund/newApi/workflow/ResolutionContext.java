package de.zrb.bund.newApi.workflow;

import java.util.List;

public interface ResolutionContext {
    boolean isAvailable(String symbol); // für Variablen und Funktionsnamen
    Object resolve(String symbol) throws UnresolvedSymbolException;
    Object invoke(String functionName, List<Object> args);
}
