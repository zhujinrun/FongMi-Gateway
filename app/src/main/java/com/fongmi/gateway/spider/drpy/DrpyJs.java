package com.fongmi.gateway.spider.drpy;

import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Util;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.jsoup.nodes.Element;
import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeJSON;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Rhino host for drpy's {@code js:} hooks.
 *
 * <p>Only two jobs: turn a rule file into JSON (the file is a bare {@code var rule = {...}}, so the
 * engine does the object-literal parsing for us), and run the small snippets that live in
 * {@code lazy} / {@code 一级} / {@code 推荐} / {@code 搜索} with the globals those snippets expect:
 * {@code request}, {@code fetch}, {@code setResult}, {@code jsp.pdfh}, {@code md5},
 * {@code base64Decode} and the {@code *_UA} constants.
 *
 * <p>Rhino rather than QuickJS because the gateway ships as one fat jar for Windows, macOS and
 * Linux; a native engine would mean a .dll, a .dylib and a .so plus an extractor. The snippets in
 * play here are ES5 with {@code let} and arrow functions, which Rhino 1.9 handles comfortably.
 *
 * <p>Every call gets a fresh scope, so concurrent site requests never share JS state.
 */
public final class DrpyJs {

    private static final MediaType FORM = MediaType.get("application/x-www-form-urlencoded; charset=utf-8");

    private static final int DEFAULT_TIMEOUT_MS = 15_000;

    private final DrpyRule rule;

    public DrpyJs(DrpyRule rule) {
        this.rule = rule == null ? DrpyRule.from(new JsonObject()) : rule;
    }

    // ------------------------------------------------------------------ rule extraction

    /**
     * Evaluate a drpy rule file and return its {@code rule} object as JSON.
     *
     * <p>The file is executed rather than pattern-matched: it may contain template helpers or an
     * {@code init()} that the owner added, and JSON.stringify is the only way to read the object
     * back without reimplementing a JS parser for {@code 5e3} and {@code !0}.
     */
    public static JsonObject extractRule(String script, String name) {
        Context cx = Context.enter();
        try {
            cx.setLanguageVersion(Context.VERSION_ECMASCRIPT);
            cx.setInterpretedMode(true);
            Scriptable scope = cx.initStandardObjects();
            // a few rule files touch the network or helper globals while building `rule`
            new DrpyJs(null).installGlobals(cx, scope, null);
            cx.evaluateString(scope, script, name, 1, null);
            Object node = ScriptableObject.getProperty(scope, "rule");
            if (node == Scriptable.NOT_FOUND || node == null) {
                throw new IllegalStateException("rule file defines no `rule` object");
            }
            Object json = NativeJSON.stringify(cx, scope, node, null, null);
            String text = Context.toString(json);
            JsonElement el = JsonParser.parseString(text);
            if (!el.isJsonObject()) throw new IllegalStateException("`rule` is not an object");
            return el.getAsJsonObject();
        } finally {
            Context.exit();
        }
    }

    // ------------------------------------------------------------------ hook execution

    /** What a snippet left behind: the returned value, plus the globals drpy reads back. */
    public static final class Outcome {
        public String inputText;
        public String inputJson;
        public String vodsJson;
        public String vodJson;

        public boolean hasInputObject() {
            return inputJson != null;
        }
    }

    public Outcome run(String code, Map<String, Object> vars) {
        Context cx = Context.enter();
        try {
            cx.setLanguageVersion(Context.VERSION_ECMASCRIPT);
            cx.setInterpretedMode(true);
            Scriptable scope = cx.initStandardObjects();
            String[] vodsSink = new String[1];
            installGlobals(cx, scope, vodsSink);
            if (vars != null) {
                for (Map.Entry<String, Object> e : vars.entrySet()) {
                    ScriptableObject.putProperty(scope, e.getKey(), Context.javaToJS(e.getValue(), scope));
                }
            }
            cx.evaluateString(scope, code, "drpy-hook", 1, null);

            Outcome out = new Outcome();
            // read the globals back through an expression, which is exactly how the snippet
            // itself saw them, rather than through the host-side property table
            Object input = read(cx, scope, "input");
            if (input instanceof CharSequence) out.inputText = input.toString();
            else if (input != null && input != Scriptable.NOT_FOUND) out.inputJson = toJson(cx, scope, input);
            out.vodsJson = vodsSink[0] != null ? vodsSink[0] : toJson(cx, scope, read(cx, scope, "VODS"));
            out.vodJson = toJson(cx, scope, read(cx, scope, "VOD"));
            return out;
        } finally {
            Context.exit();
        }
    }

    private static Object read(Context cx, Scriptable scope, String name) {
        try {
            return cx.evaluateString(scope, "typeof " + name + "==='undefined'?null:" + name, "read", 1, null);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String toJson(Context cx, Scriptable scope, Object value) {
        if (value == null || value == Scriptable.NOT_FOUND) return null;
        Object json = NativeJSON.stringify(cx, scope, value, null, null);
        return json == null ? null : Context.toString(json);
    }

    // ------------------------------------------------------------------ globals

    private void installGlobals(Context cx, Scriptable scope, String[] vodsSink) {
        ScriptableObject.putProperty(scope, "MOBILE_UA", DrpyRule.MOBILE_UA);
        ScriptableObject.putProperty(scope, "PC_UA", DrpyRule.PC_UA);
        ScriptableObject.putProperty(scope, "UC_UA", DrpyRule.UC_UA);
        ScriptableObject.putProperty(scope, "IOS_UA", DrpyRule.IOS_UA);
        ScriptableObject.putProperty(scope, "UA", DrpyRule.UA);
        ScriptableObject.putProperty(scope, "HOST", rule.host());
        ScriptableObject.putProperty(scope, "RULE_CK", "cookie");
        ScriptableObject.putProperty(scope, "VODS", cx.newArray(scope, 0));

        Scriptable fetchParams = cx.newObject(scope);
        Scriptable headers = cx.newObject(scope);
        for (Map.Entry<String, String> e : rule.headers().entrySet()) {
            ScriptableObject.putProperty(headers, e.getKey(), e.getValue());
        }
        ScriptableObject.putProperty(fetchParams, "headers", headers);
        ScriptableObject.putProperty(fetchParams, "timeout", rule.integer("timeout", DEFAULT_TIMEOUT_MS));
        ScriptableObject.putProperty(scope, "fetch_params", fetchParams);

        // setResult writes into vodsSink instead of only into the JS VODS array: the array is
        // still updated for snippets that read it, but the host never depends on that round trip
        fn(scope, "request", 2, (c, s, args) -> {
            JsonObject opts = opts(c, s, args, 1);
            return fetch(text(args, 0), method(opts), body(opts), headers(opts));
        });
        fn(scope, "fetch", 2, (c, s, args) -> {
            JsonObject opts = opts(c, s, args, 1);
            return fetch(text(args, 0), method(opts), body(opts), headers(opts));
        });
        fn(scope, "post", 3, (c, s, args) -> fetch(text(args, 0), "POST", text(args, 1), headers(opts(c, s, args, 2))));

        // jsp / jq both resolve to the DOM parser; drpy only distinguishes them for xpath support,
        // which none of the shipped rules use.
        Scriptable jsp = cx.newObject(scope);
        ScriptableObject.putProperty(jsp, "pdfh", fn(2, (c, s, args) -> DrpyDom.pdfh(text(args, 0), text(args, 1))));
        ScriptableObject.putProperty(jsp, "pd", fn(3, (c, s, args) -> DrpyDom.resolve(text(args, 2), DrpyDom.pdfh(text(args, 0), text(args, 1)))));
        ScriptableObject.putProperty(jsp, "pdfa", fn(2, (c, s, args) -> htmlArray(c, s, DrpyDom.pdfa(text(args, 0), text(args, 1)))));
        ScriptableObject.putProperty(scope, "jsp", jsp);
        ScriptableObject.putProperty(scope, "jq", jsp);

        fn(scope, "pdfh", 2, (c, s, args) -> DrpyDom.pdfh(text(args, 0), text(args, 1)));
        fn(scope, "pdfa", 2, (c, s, args) -> htmlArray(c, s, DrpyDom.pdfa(text(args, 0), text(args, 1))));
        fn(scope, "pd", 3, (c, s, args) -> DrpyDom.resolve(text(args, 2), DrpyDom.pdfh(text(args, 0), text(args, 1))));
        fn(scope, "joinUrl", 2, (c, s, args) -> DrpyDom.resolve(text(args, 0), text(args, 1)));
        fn(scope, "urljoin", 2, (c, s, args) -> DrpyDom.resolve(text(args, 0), text(args, 1)));
        fn(scope, "getProxyUrl", 1, (c, s, args) -> text(args, 0));
        fn(scope, "md5", 1, (c, s, args) -> Util.md5(text(args, 0)));
        fn(scope, "base64Encode", 1, (c, s, args) -> Util.base64(text(args, 0)));
        fn(scope, "base64Decode", 1, (c, s, args) -> Util.base64Decode(text(args, 0)));
        fn(scope, "encodeStr", 1, (c, s, args) -> text(args, 0));

        fn(scope, "setResult", 1, (c, s, args) -> {
            String mapped = mapVods(c, s, args.length > 0 ? args[0] : null);
            if (vodsSink != null) vodsSink[0] = mapped;
            // keep the JS-visible VODS in sync for snippets that read it back themselves
            return c.evaluateString(s, "VODS=" + mapped, "setResult", 1, null);
        });

        BaseFunction log = fn(1, (c, s, args) -> {
            System.err.println("[drpy] " + text(args, 0));
            return Context.getUndefinedValue();
        });
        ScriptableObject.putProperty(scope, "print", log);
        ScriptableObject.putProperty(scope, "log", log);
        Scriptable console = cx.newObject(scope);
        ScriptableObject.putProperty(console, "log", log);
        ScriptableObject.putProperty(console, "error", log);
        ScriptableObject.putProperty(console, "warn", log);
        ScriptableObject.putProperty(scope, "console", console);
    }

    /** drpy's setResult: {url,title,desc,pic_url|img,...} -> TVBox {vod_*} rows. */
    private static String mapVods(Context cx, Scriptable scope, Object raw) {
        String json = raw == null ? null : toJson(cx, scope, raw);
        JsonArray out = new JsonArray();
        if (json != null) {
            try {
                JsonElement el = JsonParser.parseString(json);
                if (el.isJsonArray()) {
                    for (JsonElement item : el.getAsJsonArray()) {
                        if (!item.isJsonObject()) continue;
                        out.add(mapVod(item.getAsJsonObject()));
                    }
                }
            } catch (Throwable ignored) {
                // a snippet pushing something non-serialisable should yield an empty list, not throw
            }
        }
        // Rhino 1.9 requires a non-null reviver here; identity keeps JSON.parse semantics
        return out.toString();
    }

    private static JsonObject mapVod(JsonObject it) {
        JsonObject o = new JsonObject();
        o.addProperty("vod_id", pick(it, "url"));
        o.addProperty("vod_name", pick(it, "title"));
        o.addProperty("vod_remarks", pick(it, "desc"));
        o.addProperty("vod_content", pick(it, "content"));
        String pic = pick(it, "pic_url");
        o.addProperty("vod_pic", pic.isEmpty() ? pick(it, "img") : pic);
        copyIf(it, o, "tname", "type_name");
        copyIf(it, o, "tid", "type_id");
        copyIf(it, o, "year", "vod_year");
        copyIf(it, o, "actor", "vod_actor");
        copyIf(it, o, "director", "vod_director");
        copyIf(it, o, "area", "vod_area");
        return o;
    }

    private static void copyIf(JsonObject from, JsonObject to, String src, String dst) {
        if (from.has(src) && !from.get(src).isJsonNull()) to.add(dst, from.get(src));
    }

    private static String pick(JsonObject o, String key) {
        JsonElement el = o.get(key);
        if (el == null || el.isJsonNull()) return "";
        return el.isJsonPrimitive() ? el.getAsString() : el.toString();
    }

    private static Object htmlArray(Context cx, Scriptable scope, List<Element> nodes) {
        Scriptable arr = cx.newArray(scope, nodes.size());
        for (int i = 0; i < nodes.size(); i++) {
            ScriptableObject.putProperty(arr, i, nodes.get(i).outerHtml());
        }
        return arr;
    }

    // ------------------------------------------------------------------ http

    /** GET/POST with the rule's headers and charset; shared with {@link DrpySpider}. */
    public String fetch(String url, String method, String data, Map<String, String> headers) {
        String target = rule.absolute(url);
        if (target.isEmpty()) return "";
        Map<String, String> merged = new LinkedHashMap<>(rule.headers());
        if (headers != null) merged.putAll(headers);
        try {
            Request.Builder builder = new Request.Builder().url(target);
            for (Map.Entry<String, String> e : merged.entrySet()) {
                try {
                    builder.header(e.getKey(), e.getValue());
                } catch (Throwable ignored) {
                    // non-ASCII or malformed header values are dropped rather than failing the call
                }
            }
            if ("POST".equals(method)) {
                builder.post(RequestBody.create(data == null ? "" : data, FORM));
            } else {
                builder.get();
            }
            int timeout = Math.max(3000, rule.integer("timeout", DEFAULT_TIMEOUT_MS));
            try (Response response = OkHttp.client(timeout).newCall(builder.build()).execute()) {
                ResponseBody body = response.body();
                if (body == null) return "";
                byte[] bytes = body.bytes();
                return new String(bytes, charset(response));
            }
        } catch (Throwable t) {
            System.err.println("[drpy] request fail " + target + " -> " + t);
            return "";
        }
    }

    private Charset charset(Response response) {
        String configured = rule.str("encoding");
        if (!configured.isEmpty()) {
            try {
                return Charset.forName(configured);
            } catch (Throwable ignored) {
            }
        }
        MediaType type = response.body() == null ? null : response.body().contentType();
        Charset cs = type == null ? null : type.charset(StandardCharsets.UTF_8);
        return cs == null ? StandardCharsets.UTF_8 : cs;
    }

    /** Reads a snippet-supplied options object ({@code {headers, method, data}}) as JSON. */
    private static JsonObject opts(Context cx, Scriptable scope, Object[] args, int index) {
        if (args.length <= index || args[index] == null) return new JsonObject();
        String json = toJson(cx, scope, args[index]);
        if (json == null) return new JsonObject();
        try {
            JsonElement el = JsonParser.parseString(json);
            return el.isJsonObject() ? el.getAsJsonObject() : new JsonObject();
        } catch (Throwable t) {
            return new JsonObject();
        }
    }

    private static Map<String, String> headers(JsonObject opts) {
        Map<String, String> map = new LinkedHashMap<>();
        if (opts == null || !opts.has("headers") || !opts.get("headers").isJsonObject()) return map;
        for (Map.Entry<String, JsonElement> e : opts.getAsJsonObject("headers").entrySet()) {
            map.put(e.getKey(), e.getValue().isJsonPrimitive()
                    ? e.getValue().getAsString() : e.getValue().toString());
        }
        return map;
    }

    private static String method(JsonObject opts) {
        if (opts == null || !opts.has("method")) return "GET";
        String m = opts.get("method").getAsString();
        return "post".equalsIgnoreCase(m) ? "POST" : "GET";
    }

    /** {@code data} may be a raw string or a key/value object, which drpy form-encodes. */
    private static String body(JsonObject opts) {
        if (opts == null) return null;
        JsonElement data = opts.has("data") ? opts.get("data") : opts.get("body");
        if (data == null || data.isJsonNull()) return null;
        if (data.isJsonPrimitive()) return data.getAsString();
        if (!data.isJsonObject()) return null;
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, JsonElement> e : data.getAsJsonObject().entrySet()) {
            if (sb.length() > 0) sb.append('&');
            sb.append(java.net.URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)).append('=')
                    .append(java.net.URLEncoder.encode(e.getValue().getAsString(), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private static String text(Object[] args, int index) {
        if (args.length <= index || args[index] == null) return "";
        return Context.toString(args[index]);
    }

    // ------------------------------------------------------------------ function helpers

    private interface Body {
        Object call(Context cx, Scriptable scope, Object[] args);
    }

    private static void fn(Scriptable scope, String name, int arity, Body body) {
        ScriptableObject.putProperty(scope, name, fn(arity, body));
    }

    private static BaseFunction fn(int arity, Body body) {
        return new BaseFunction() {
            @Override
            public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
                return body.call(cx, scope, args == null ? new Object[0] : args);
            }

            @Override
            public int getArity() {
                return arity;
            }
        };
    }
}
