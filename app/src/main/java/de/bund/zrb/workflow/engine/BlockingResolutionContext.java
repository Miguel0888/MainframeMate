package de.bund.zrb.workflow.engine;

import de.bund.zrb.runtime.ExpressionRegistryImpl;
import de.zrb.bund.newApi.workflow.ResolutionContext;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

public class BlockingResolutionContext implements ResolutionContext {

    private final Map<String, Object> symbolTable = new ConcurrentHashMap<>();
    private final Map<String, CountDownLatch> waiting = new ConcurrentHashMap<>();

    private final Set<String> knownFunctions;

    public BlockingResolutionContext() {
        this.knownFunctions = ExpressionRegistryImpl.getInstance().getKeys();
    }

    public void provide(String name, Object value) {
        symbolTable.put(name, value);
        CountDownLatch latch = waiting.get(name);
        if (latch != null) {
            latch.countDown();
        }
    }

    @Override
    public boolean isAvailable(String name) {
        // Wenn Variable vorhanden → sofort verfügbar
        if (symbolTable.containsKey(name)) {
            return true;
        }

        // Wenn der Name eine bekannte Funktion ist → nicht blockieren
        if (knownFunctions.contains(name)) {
            return true;
        }

        // Ansonsten warten
        CountDownLatch latch = waiting.computeIfAbsent(name, k -> new CountDownLatch(1));
        try {
            latch.await(); // ⏳ blockiert bis provide(...)
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for symbol: " + name);
        }

        return true;
    }


    @Override
    public Object resolve(String name) {
        return symbolTable.get(name);
    }

    @Override
    public Object invoke(String functionName, List<Object> args) {
        if (!knownFunctions.contains(functionName)) {
            throw new IllegalArgumentException("Unbekannte Funktion: " + functionName);
        }
        try {
            // Funktionen werden synchron (nicht-blockierend) ausgeführt
            return ExpressionRegistryImpl.getInstance().evaluate(functionName, args.stream()
                    .map(Object::toString)
                    .collect(Collectors.toList()));
        } catch (Exception e) {
            throw new IllegalArgumentException("Fehler beim Aufruf der Funktion: " + functionName, e);
        }
    }

}
