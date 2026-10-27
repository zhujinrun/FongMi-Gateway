package com.fongmi.gateway.spider.drpy;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A drpy rule file, read as data.
 *
 * <p>The three sources this backs are nothing but {@code var rule = {...}} -- no code outside the
 * occasional {@code js:} hook -- so the gateway evaluates the file once with Rhino purely to turn
 * the object literal into JSON, then interprets the fields natively. Rule updates from the site
 * owner are therefore data changes, not code changes.
 */
public final class DrpyRule {

    /** UA aliases the framework expands inside {@code headers}. */
    public static final String MOBILE_UA = "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36"
            + " (KHTML, like Gecko) Chrome/90.0.4430.91 Mobile Safari/537.36";
    public static final String PC_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
            + " (KHTML, like Gecko) Chrome/95.0.4638.54 Safari/537.36";
    public static final String UC_UA = "Mozilla/5.0 (Linux; U; Android 9; zh-CN; MI 9 Build/PKQ1.181121.001)"
            + " AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/57.0.2987.108 UCBrowser/12.5.5.1035"
            + " Mobile Safari/537.36";
    public static final String IOS_UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 13_2_3 like Mac OS X)"
            + " AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.0.3 Mobile/15E148 Safari/604.1";
    public static final String UA = "Mozilla/5.0";

    private final JsonObject raw;

    private DrpyRule(JsonObject raw) {
        this.raw = raw;
    }

    public static DrpyRule from(JsonObject raw) {
        return new DrpyRule(raw == null ? new JsonObject() : raw);
    }

    public JsonObject raw() {
        return raw;
    }

    public String str(String key) {
        JsonElement el = raw.get(key);
        if (el == null || el.isJsonNull()) return "";
        if (el.isJsonPrimitive()) return el.getAsString();
        return el.toString();
    }

    public boolean flag(String key) {
        JsonElement el = raw.get(key);
        return el != null && !el.isJsonNull() && el.isJsonPrimitive() && el.getAsBoolean();
    }

    public int integer(String key, int fallback) {
        try {
            JsonElement el = raw.get(key);
            return el == null || el.isJsonNull() ? fallback : el.getAsInt();
        } catch (Throwable t) {
            return fallback;
        }
    }

    public boolean has(String key) {
        JsonElement el = raw.get(key);
        return el != null && !el.isJsonNull();
    }

    public String title() {
        return str("title");
    }

    public String host() {
        String h = str("host");
        if (h.isEmpty()) return "";
        return h.endsWith("/") ? h.substring(0, h.length() - 1) : h;
    }

    public String homeUrl() {
        return str("homeUrl");
    }

    public String url() {
        return str("url");
    }

    public String detailUrl() {
        return str("detailUrl");
    }

    public String searchUrl() {
        return str("searchUrl");
    }

    public String lazy() {
        return str("lazy");
    }

    public boolean playParse() {
        return flag("play_parse");
    }

    public boolean doubleList() {
        return flag("double");
    }

    public int limit() {
        return integer("limit", 20);
    }

    /** {@code 推荐} / {@code 一级} / {@code 二级} / {@code 搜索} live under Chinese keys by design. */
    public String pick(String key) {
        return str(key);
    }

    /** Absolute base for resolving the rule's relative URLs. */
    public String base() {
        String h = host();
        return h.isEmpty() ? "" : h + "/";
    }

    public String absolute(String path) {
        if (path == null) return "";
        String p = path.trim();
        if (p.isEmpty()) return "";
        if (p.startsWith("http://") || p.startsWith("https://")) return p;
        if (p.startsWith("//")) return "https:" + p;
        return DrpyDom.resolve(base(), p);
    }

    /**
     * Request headers with the framework's UA aliases expanded, e.g.
     * {@code "User-Agent": "MOBILE_UA"}. A cookie value that is a URL is fetched by drpy on init;
     * we leave that alone until a source actually needs it.
     */
    public Map<String, String> headers() {
        Map<String, String> map = new LinkedHashMap<>();
        JsonElement el = raw.get("headers");
        if (el == null || !el.isJsonObject()) return map;
        for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
            String v = e.getValue().isJsonPrimitive() ? e.getValue().getAsString() : e.getValue().toString();
            if ("user-agent".equalsIgnoreCase(e.getKey())) v = ua(v);
            map.put(e.getKey(), v);
        }
        return map;
    }

    private static String ua(String v) {
        return switch (v) {
            case "MOBILE_UA" -> MOBILE_UA;
            case "PC_UA" -> PC_UA;
            case "UC_UA" -> UC_UA;
            case "IOS_UA" -> IOS_UA;
            case "UA" -> UA;
            default -> v;
        };
    }

    /** {@code filter} is passed through to the client untouched -- it is already TVBox shaped. */
    public JsonObject filter() {
        JsonElement el = raw.get("filter");
        return el != null && el.isJsonObject() ? el.getAsJsonObject() : null;
    }

    public String filterUrl() {
        return str("filter_url");
    }

    public JsonObject filterDef() {
        JsonElement el = raw.get("filter_def");
        return el != null && el.isJsonObject() ? el.getAsJsonObject() : null;
    }
}
