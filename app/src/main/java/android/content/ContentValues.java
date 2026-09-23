package android.content;

import java.util.HashMap;
import java.util.Map;

public class ContentValues {
    private final Map<String, Object> values = new HashMap<>();

    public void put(String key, String value) {
        values.put(key, value);
    }

    public void put(String key, Integer value) {
        values.put(key, value);
    }

    public void put(String key, Long value) {
        values.put(key, value);
    }

    public void put(String key, Boolean value) {
        values.put(key, value);
    }

    public void put(String key, byte[] value) {
        values.put(key, value);
    }

    public Object get(String key) {
        return values.get(key);
    }

    public String getAsString(String key) {
        Object v = values.get(key);
        return v == null ? null : String.valueOf(v);
    }

    public Integer getAsInteger(String key) {
        Object v = values.get(key);
        return v == null ? null : Integer.valueOf(String.valueOf(v));
    }

    public Map<String, Object> asMap() {
        return values;
    }

    public int size() {
        return values.size();
    }
}
