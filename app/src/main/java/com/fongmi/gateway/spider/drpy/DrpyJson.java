package com.fongmi.gateway.spider.drpy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The {@code json:} half of the drpy selector dialect.
 *
 * <p>A rule such as {@code "json:data.items;name;image;duration_string;video_id"} walks a JSON
 * document the same way the CSS form walks a DOM: the first field is the list path, the rest are
 * read off every element of that list. Paths are dotted, may index arrays numerically
 * ({@code response.3.docs}), accept {@code $} as a root marker and {@code ||} as an alternative
 * chain -- all of which show up in the three rules this gateway has to run.
 */
public final class DrpyJson {

    private static final Pattern INDEX = Pattern.compile("([^\\[]*)\\[(\\d+)]");

    private DrpyJson() {
    }

    /**
     * drpy wraps JSON endpoints in whatever the vendor feels like; {@code dealJson} pulls the
     * outermost {...} out of "cb({...})" and trailing-comment junk before parsing.
     */
    public static JsonElement deal(String body) {
        if (body == null) return null;
        String text = body.trim();
        if (text.isEmpty()) return null;
        JsonElement direct = tryParse(text);
        if (direct != null) return direct;
        int start = firstObjectStart(text);
        int end = Math.max(text.lastIndexOf('}'), text.lastIndexOf(']'));
        if (start >= 0 && end > start) return tryParse(text.substring(start, end + 1));
        return null;
    }

    private static int firstObjectStart(String text) {
        int brace = text.indexOf('{');
        int bracket = text.indexOf('[');
        if (brace < 0) return bracket;
        if (bracket < 0) return brace;
        return Math.min(brace, bracket);
    }

    private static JsonElement tryParse(String text) {
        try {
            return JsonParser.parseString(text);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Walk a drpy JSON path; returns null when any hop is missing. */
    public static JsonElement jp(String path, JsonElement root) {
        if (root == null || path == null) return null;
        String p = path.trim();
        for (String alt : p.split("\\|\\|")) {
            JsonElement found = jpOne(alt.trim(), root);
            if (found != null && !found.isJsonNull()) return found;
        }
        return null;
    }

    private static JsonElement jpOne(String path, JsonElement root) {
        String p = path.trim();
        if (p.isEmpty() || "$".equals(p)) return root;
        if (p.startsWith("$.")) p = p.substring(2);
        else if (p.startsWith("$")) p = p.substring(1);
        if (p.startsWith(".")) p = p.substring(1);
        JsonElement cur = root;
        for (String token : p.split("\\.")) {
            if (cur == null || token.isEmpty()) continue;
            cur = step(cur, token);
        }
        return cur;
    }

    private static JsonElement step(JsonElement cur, String token) {
        Matcher m = INDEX.matcher(token);
        String name = token;
        Integer index = null;
        if (m.matches()) {
            name = m.group(1);
            index = Integer.parseInt(m.group(2));
        }
        if (!name.isEmpty()) {
            if (!cur.isJsonObject()) return null;
            JsonObject obj = cur.getAsJsonObject();
            cur = obj.has(name) ? obj.get(name) : null;
            if (cur == null) return null;
        }
        if (index != null) {
            if (!cur.isJsonArray()) return null;
            JsonArray arr = cur.getAsJsonArray();
            if (index >= arr.size()) return null;
            cur = arr.get(index);
        }
        return cur;
    }

    /** First value at {@code path}, as the rule would see it (numbers become their literal text). */
    public static String pdfh(JsonElement node, String path) {
        if (path == null || path.trim().isEmpty()) return "";
        JsonElement value = jp(path.trim(), node);
        return stringify(value);
    }

    public static String stringify(JsonElement value) {
        if (value == null || value.isJsonNull()) return "";
        if (value.isJsonPrimitive()) {
            JsonPrimitive p = value.getAsJsonPrimitive();
            return p.getAsString();
        }
        return value.toString();
    }

    /** The list half: {@code path} should land on an array. */
    public static List<JsonElement> pdfa(JsonElement node, String path) {
        List<JsonElement> out = new ArrayList<>();
        if (path == null || path.trim().isEmpty()) return out;
        JsonElement value = jp(path.trim(), node);
        if (value == null) return out;
        if (value.isJsonArray()) {
            JsonArray arr = value.getAsJsonArray();
            // "items" often resolves to [[...]] when the endpoint nests one wrapper level
            if (arr.size() == 1 && arr.get(0).isJsonArray()) arr = arr.get(0).getAsJsonArray();
            arr.forEach(out::add);
        } else if (value.isJsonObject()) {
            out.add(value);
        }
        return out;
    }
}
