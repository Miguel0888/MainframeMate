package de.zrb.bund.newApi.sentence;

import java.util.LinkedHashMap;
import java.util.Map;

public class SentenceTypeSpec {

    private Map<String, SentenceDefinition> definitions  = new LinkedHashMap<>();

    public Map<String, SentenceDefinition> getDefinitions() {
        return definitions;
    }

    public void setDefinitions(Map<String, SentenceDefinition> definitions) {
        this.definitions = definitions != null ? definitions : new LinkedHashMap<>();
    }
}