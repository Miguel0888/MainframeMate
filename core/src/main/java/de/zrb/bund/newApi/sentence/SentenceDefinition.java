package de.zrb.bund.newApi.sentence;

import com.google.gson.*;
import com.google.gson.annotations.JsonAdapter;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

@JsonAdapter(SentenceDefinition.SentenceDefinitionAdapter.class)
public class SentenceDefinition {
    private SentenceMeta meta;
    private final Map<FieldCoordinate, SentenceField> fields = new TreeMap<>();

    class SentenceDefinitionAdapter implements JsonSerializer<FieldCoordinate>, JsonDeserializer<FieldCoordinate> {
        @Override
        public JsonElement serialize(FieldCoordinate src, Type typeOfSrc, JsonSerializationContext context) throws JsonParseException {
            return context.serialize(src);
        }
        @Override
        public SentenceDefinition deserialize( JsonElement json, Type typeOfT, JsonSerializationContext context) {
            return null;
        }
    }

    public SentenceMeta getMeta() {
        return meta;
    }

    public void setMeta(SentenceMeta meta) {
        this.meta = meta;
    }

    public Map<FieldCoordinate, SentenceField> getFields() {
        return fields;
    }

    public void setFields(Map<FieldCoordinate, SentenceField> newFields) {
        this.fields.clear();
        if (newFields != null) {
            this.fields.putAll(newFields);
        }
    }

    /**
     * Gibt die maximale Zeilenanzahl aller Felder zurück.
     */
    public Integer getRowCount() {
        return fields.keySet().stream()
                .map(FieldCoordinate::getRow)
                .max(Integer::compareTo)
                .orElse(null);
    }

    /**
     * Fügt ein neues Feld an der gegebenen Koordinate hinzu, falls diese noch nicht belegt ist.
     *
     * @param coord Position und Zeile des Feldes
     * @param field Das Feldobjekt
     * @return true, wenn eingefügt wurde; false, wenn die Position bereits vergeben ist
     */
    public boolean addField(FieldCoordinate coord, SentenceField field) {
        if (fields.containsKey(coord)) {
            return false;
        }
        fields.put(coord, field);
        return true;
    }
}
