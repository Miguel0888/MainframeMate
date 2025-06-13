package de.zrb.bund.newApi;

import java.util.concurrent.TimeoutException;

public interface VariableRegistry {
    void set(String key, String value);

    String get(String key, long timeoutMillis) throws TimeoutException, InterruptedException;

    boolean contains(String key);

    void clear();
}
