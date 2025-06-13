package de.bund.zrb.runtime;

import de.zrb.bund.newApi.VariableRegistry;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.stream.Collectors;

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
    public Set<String> getKeys() {
        return new HashSet<>(variables.keySet());
    }

    @Override
    public void clear() {
        variables.clear();
    }

    @Override
    public Map<String, String> getAllVariables() {
        Map<String, String> resolved = new ConcurrentHashMap<>();
        for (Map.Entry<String, CompletableFuture<String>> entry : variables.entrySet()) {
            if (entry.getValue().isDone()) {
                try {
                    resolved.put(entry.getKey(), entry.getValue().getNow(null));
                } catch (Exception ignored) {
                    // Falls future fehlschlägt, ignoriere diesen Eintrag
                }
            }
        }
        return resolved;
    }

    @Override
    public boolean has(String name) {
        return false; // ToDO
    }
}
