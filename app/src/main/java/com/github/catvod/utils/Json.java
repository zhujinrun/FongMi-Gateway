package com.github.catvod.utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Json {

    public static JsonElement parse(String json) {
        return JsonParser.parseString(json);
    }

    public static boolean isObj(String text) {
        try {
            if (text == null || text.trim().isEmpty()) return false;
            String t = text.trim();
            if (!t.startsWith("{")) return false;
            JsonParser.parseString(t).getAsJsonObject();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isArray(String text) {
        try {
            if (text == null || text.trim().isEmpty()) return false;
            String t = text.trim();
            if (!t.startsWith("[")) return false;
            JsonParser.parseString(t).getAsJsonArray();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isEmpty(JsonObject obj, String key) {
        if (obj == null || !obj.has(key)) return true;
        JsonElement element = obj.get(key);
        if (element.isJsonNull()) return true;
        if (element.isJsonArray()) return element.getAsJsonArray().isEmpty();
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) return element.getAsString().trim().isEmpty();
        return false;
    }

    public static String safeString(JsonObject obj, String key) {
        try {
            return obj.getAsJsonPrimitive(key).getAsString().trim();
        } catch (Exception e) {
            return "";
        }
    }

    public static List<String> safeListString(JsonObject obj, String key) {
        List<String> result = new ArrayList<>();
        if (obj == null || !obj.has(key)) return result;
        if (obj.get(key).isJsonPrimitive()) {
            String s = obj.get(key).getAsString();
            if (s != null && !s.trim().isEmpty()) result.add(s.trim());
        } else if (obj.get(key).isJsonArray()) {
            for (JsonElement opt : obj.getAsJsonArray(key)) {
                if (opt.isJsonPrimitive()) result.add(opt.getAsString());
            }
        }
        return result;
    }

    public static List<JsonElement> safeListElement(JsonObject obj, String key) {
        List<JsonElement> result = new ArrayList<>();
        if (obj == null || !obj.has(key)) return result;
        if (obj.get(key).isJsonObject()) result.add(obj.get(key).getAsJsonObject());
        else if (obj.get(key).isJsonArray()) {
            for (JsonElement opt : obj.getAsJsonArray(key)) {
                if (opt.isJsonObject()) result.add(opt.getAsJsonObject());
            }
        }
        return result;
    }

    public static JsonObject safeObject(JsonElement element) {
        try {
            if (element.isJsonPrimitive()) element = parse(element.getAsJsonPrimitive().getAsString());
            return element.getAsJsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    public static Map<String, String> toMap(String json) {
        if (json == null || json.isEmpty()) return null;
        return toMap(parse(json));
    }

    public static Map<String, String> toMap(JsonElement element) {
        Map<String, String> map = new HashMap<>();
        JsonObject object = safeObject(element);
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) map.put(entry.getKey(), safeString(object, entry.getKey()));
        return map;
    }
}
