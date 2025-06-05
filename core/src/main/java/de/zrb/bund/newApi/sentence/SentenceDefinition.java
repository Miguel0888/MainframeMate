package de.zrb.bund.newApi.sentence;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public class SentenceDefinition {

    private SentenceMeta meta;
    private Map<Integer, SentenceField> fields = new TreeMap<>();

    public SentenceMeta getMeta() {
        return meta;
    }

    public void setMeta(SentenceMeta meta) {
        this.meta = meta;
    }

    public Map<Integer, SentenceField> getFields() {
        return fields;
    }

    public void setFields(Map<Integer, SentenceField> fields) {
        this.fields = fields != null ? fields : new TreeMap<>();
    }

    public Integer getRowCount() {
        return fields.values().stream()
                .map(SentenceField::getRow)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(null);
    }

    public boolean addField(SentenceDefinition def, SentenceField field) {
        Integer pos = field.getPosition();
        if (pos == null || def.getFields().containsKey(pos)) {
            return false; // pos already in use
        }
        def.getFields().put(pos, field);
        return true;
    }

}
