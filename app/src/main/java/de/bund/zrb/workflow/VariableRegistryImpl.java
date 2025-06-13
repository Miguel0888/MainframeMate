package de.bund.zrb.workflow;

import de.zrb.bund.newApi.VariableRegistry;

import java.util.Map;
import java.util.concurrent.*;

public class VariableRegistryImpl implements VariableRegistry {

    private final Map<String, CompletableFuture<String>> variables = new ConcurrentHashMap<>();

    private static VariableRegistryImpl instance;

    private VariableRegistryImpl() {
        // private, damit keine externe Instanzierung möglich ist
    }

    /**
     * Liefert die Singleton-Instanz der ToolRegistry.
     */
    public static synchronized VariableRegistryImpl getInstance() {
        if (instance == null) {
            instance = new VariableRegistryImpl();
        }
        return instance;
    }

    @Override
    public void set(String key, String value) {
        variables.computeIfAbsent(key, k -> new CompletableFuture<>()).complete(value);
    }

    @Override
    public String get(String key, long timeoutMillis) throws TimeoutException, InterruptedException {
        CompletableFuture<String> future = variables.computeIfAbsent(key, k -> new CompletableFuture<>());
        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            throw new RuntimeException("Variable resolution failed", e);
        }
    }

    @Override
    public boolean contains(String key) {
        return variables.containsKey(key) && variables.get(key).isDone();
    }

    @Override
    public void clear() {
        variables.clear();
    }
}
