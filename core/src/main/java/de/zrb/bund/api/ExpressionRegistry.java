package de.zrb.bund.api;

import java.util.List;
import java.util.Optional;

public interface ExpressionRegistry {
    void register(String key, String code);

    Optional<String> getCode(String key);

    String getSource(String key);

    String evaluate(String key, List<String> args) throws Exception;

    void remove(String key);

    String[] getKeys();

    void reload();

    void save();
}
