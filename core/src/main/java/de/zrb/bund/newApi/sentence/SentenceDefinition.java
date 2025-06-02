package de.zrb.bund.newApi.sentence;

import java.util.List;

public class SentenceDefinition {

    private SentenceMeta meta;
    private List<SentenceField> fields;

    public SentenceMeta getMeta() {
        return meta;
    }

    public void setMeta(SentenceMeta meta) {
        this.meta = meta;
    }

    public List<SentenceField> getFields() {
        return fields;
    }

    public void setFields(List<SentenceField> fields) {
        this.fields = fields;
    }
}