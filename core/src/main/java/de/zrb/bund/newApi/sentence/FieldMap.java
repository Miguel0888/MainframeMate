package de.zrb.bund.newApi.sentence;

import com.google.gson.*;
import com.google.gson.annotations.JsonAdapter;

import java.lang.reflect.Type;
import java.util.*;

/**
 * Diese Klasse ist erforderlich, da GSON (ohne aufwendige config) Maps von Objekten nicht richtig serialisieren kann
 */
@JsonAdapter(FieldMap.FieldMapAdapter.class)
public class FieldMap {

    private final Map<FieldCoordinate, SentenceField> delegate = new TreeMap<>(new FieldCoordinateComparator());

    public void put(FieldCoordinate key, SentenceField value) {
        delegate.put(key, value);
    }

    public SentenceField get(FieldCoordinate key) {
        return delegate.get(key);
    }

    public SentenceField remove(FieldCoordinate key) {
        return delegate.remove(key);
    }

    public Set<Map.Entry<FieldCoordinate, SentenceField>> entrySet() {
        return delegate.entrySet();
    }

    public boolean containsKey(FieldCoordinate key) {
        return delegate.containsKey(key);
    }

    public Set<FieldCoordinate> keySet() {
        return delegate.keySet();
    }

    public Collection<SentenceField> values() {
        return delegate.values();
    }

    public int size() {
        return delegate.size();
    }

    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    public void clear() {
        delegate.clear();
    }

    // Optional: getter für den internen Map-Zustand
    public Map<FieldCoordinate, SentenceField> asMap() {
        return Collections.unmodifiableMap(delegate);
    }

    public void putAll(FieldMap map) {
        if (map != null) {
            for (Map.Entry<FieldCoordinate, SentenceField> entry : map.entrySet()) {
                delegate.put(entry.getKey(), entry.getValue());
            }
        }
    }

    public static class FieldMapAdapter implements JsonSerializer<FieldMap>, JsonDeserializer<FieldMap> {
        @Override
        public JsonElement serialize(FieldMap src, Type typeOfSrc, JsonSerializationContext context) {
            JsonArray array = new JsonArray();
            for (Map.Entry<FieldCoordinate, SentenceField> entry : src.entrySet()) {
                JsonObject obj = new JsonObject();
                obj.add("coordinate", context.serialize(entry.getKey()));
                obj.add("field", context.serialize(entry.getValue()));
                array.add(obj);
            }
            return array;
        }

        @Override
        public FieldMap deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
            FieldMap map = new FieldMap();
            for (JsonElement el : json.getAsJsonArray()) {
                JsonObject obj = el.getAsJsonObject();
                FieldCoordinate coord = context.deserialize(obj.get("coordinate"), FieldCoordinate.class);
                SentenceField field = context.deserialize(obj.get("field"), SentenceField.class);
                map.put(coord, field);
            }
            return map;
        }
    }

    private static class FieldCoordinateComparator implements Comparator<FieldCoordinate> {
        @Override
        public int compare(FieldCoordinate a, FieldCoordinate b) {
            int rowCompare = Integer.compare(a.getRow(), b.getRow());
            return (rowCompare != 0) ? rowCompare : Integer.compare(a.getPosition(), b.getPosition());
        }
    }
}
