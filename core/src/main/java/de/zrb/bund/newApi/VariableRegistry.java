package de.zrb.bund.newApi;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;

public interface VariableRegistry {
    void set(String key, String value);

    String get(String key, long timeoutMillis) throws TimeoutException, InterruptedException;

    boolean contains(String key);

    Set<String> getKeys();

    void clear();

    Map<String, String> getAllVariables();

    boolean has(String name);
}
