package de.bund.zrb.workflow.engine;

import de.zrb.bund.newApi.workflow.ResolutionContext;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

public class BlockingResolutionContext implements ResolutionContext {

    private final Map<String, Object> symbolTable = new ConcurrentHashMap<>();
    private final Map<String, CountDownLatch> waiting = new ConcurrentHashMap<>();

    public void provide(String name, Object value) {
        symbolTable.put(name, value);
        CountDownLatch latch = waiting.get(name);
        if (latch != null) {
            latch.countDown();
        }
    }

    @Override
    public boolean isAvailable(String name) {
        if (symbolTable.containsKey(name)) {
            return true;
        }

        CountDownLatch latch = waiting.computeIfAbsent(name, k -> new CountDownLatch(1));
        try {
            latch.await(); // ⏳ Blockiert, bis provide(...) aufgerufen wird
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
        // Beispiel-Stub:
        if ("wrap".equals(functionName)) {
            return "[" + args.get(0) + "]";
        }
        throw new IllegalArgumentException("Unknown function: " + functionName);
    }
}
