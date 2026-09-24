package com.fongmi.gateway.server;

import com.fongmi.gateway.GsonHolder;
import com.fongmi.gateway.api.SiteApi;
import com.fongmi.gateway.config.Site;
import com.fongmi.gateway.config.VodConfig;
import com.fongmi.gateway.loader.BaseLoader;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;

public class GatewayServer {

    private final String host;
    private final int port;
    private final String token;
    private HttpServer server;

    public GatewayServer(String host, int port, String token) {
        this.host = host;
        this.port = port;
        this.token = token == null ? "" : token;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(host, port), 0);
        server.setExecutor(Executors.newFixedThreadPool(16));
        server.createContext("/", this::handle);
        server.start();
        System.out.println("[gateway] listening on http://" + host + ":" + port);
        if (!token.isEmpty()) System.out.println("[gateway] token required: " + token);
    }

    public void stop() {
        if (server != null) server.stop(0);
    }

    private void handle(HttpExchange ex) throws IOException {
        long start = System.currentTimeMillis();
        try {
            if (!auth(ex)) {
                sendJson(ex, 401, error("unauthorized"));
                return;
            }
            String path = ex.getRequestURI().getPath();
            Map<String, String> q = query(ex);
            String method = ex.getRequestMethod();
            String body = "POST".equalsIgnoreCase(method) ? readBody(ex) : "";

            if ("OPTIONS".equalsIgnoreCase(method)) {
                sendJson(ex, 204, "");
                return;
            }

            String response;
            if (isCatvodPath(path)) {
                response = handleCatvod(path, q, body);
                sendJson(ex, 200, response);
                return;
            }
            switch (path) {
                case "/health" -> response = ok(summary());
                case "/config" -> response = handleConfig(q, body);
                case "/sites" -> response = ok(GsonHolder.GSON.toJsonTree(sites()));
                case "/home" -> response = ok(single(SiteApi.homeContent(req(q, body, "site", "key"))));
                case "/category" -> response = ok(single(SiteApi.categoryContent(
                        req(q, body, "site", "key"),
                        req(q, body, "tid", "typeId"),
                        req(q, body, "pg", "page"),
                        bool(q, body, "filter", true),
                        ext(q, body))));
                case "/detail" -> response = ok(single(SiteApi.detailContent(
                        req(q, body, "site", "key"),
                        req(q, body, "id", "ids"))));
                case "/search" -> response = ok(single(SiteApi.searchContent(
                        req(q, body, "site", "key"),
                        req(q, body, "key", "word", "wd", "keyword"),
                        bool(q, body, "quick", false),
                        req(q, body, "pg", "page"))));
                case "/player" -> response = ok(single(SiteApi.playerContent(
                        req(q, body, "site", "key"),
                        req(q, body, "flag"),
                        req(q, body, "id", "url"))));
                case "/live" -> response = ok(single(SiteApi.liveContent(
                        req(q, body, "name"),
                        req(q, body, "url"))));
                case "/proxy" -> response = handleProxy(ex, q, body);
                default -> {
                    if (path.startsWith("/rpc")) {
                        response = handleRpc(q, body);
                    } else {
                        response = error("not found: " + path);
                        sendJson(ex, 404, response);
                        return;
                    }
                }
            }
            sendJson(ex, 200, response);
        } catch (Exception e) {
            e.printStackTrace();
            sendJson(ex, 500, error(e.getClass().getSimpleName() + ": " + e.getMessage()));
        } finally {
            long ms = System.currentTimeMillis() - start;
            if (ms > 500) System.err.println("[gateway] slow " + ms + "ms " + ex.getRequestURI());
        }
    }

    private static boolean isCatvodPath(String path) {
        if (path == null) return false;
        String[] p = path.split("/");
        return p.length == 3 && !p[1].isEmpty() && switch (p[2]) {
            case "init", "home", "category", "detail", "search", "play" -> true;
            default -> false;
        };
    }

    /** Player catvod[type8]: POST /{siteKey}/{init|home|category|detail|search|play} → raw TVBox JSON (no envelope). */
    private String handleCatvod(String path, Map<String, String> q, String body) throws Exception {
        String[] p = path.split("/");
        String key = p[1];
        String method = p[2];
        JsonObject o = bodyJson(body);
        try {
            return switch (method) {
                case "init" -> "{}";
                case "home" -> SiteApi.homeContent(key);
                case "category" -> {
                    String tid = first(q.get("id"), q.get("tid"), str(o, "id", "tid", "typeId"));
                    String pg = first(q.get("page"), q.get("pg"), str(o, "page", "pg"));
                    Map<String, String> ext = catvodExt(o);
                    boolean filter = !ext.isEmpty() || bool(q, body, "filter", false);
                    yield SiteApi.categoryContent(key, tid, pg, filter, ext);
                }
                case "detail" -> SiteApi.detailContent(key, first(q.get("id"), q.get("ids"), str(o, "id", "ids")));
                case "search" -> SiteApi.searchContent(key,
                        first(q.get("wd"), q.get("key"), q.get("word"), str(o, "wd", "key", "word", "keyword")),
                        bool(q, body, "quick", false),
                        first(q.get("pg"), q.get("page"), str(o, "pg", "page")));
                case "play" -> SiteApi.playerContent(key,
                        first(q.get("flag"), str(o, "flag")),
                        first(q.get("id"), q.get("url"), str(o, "id", "url")));
                default -> throw new Exception("unknown catvod method: " + method);
            };
        } catch (Exception e) {
            System.err.println("[gateway] catvod " + method + " site=" + key + " failed: " + e.getMessage());
            throw e;
        }
    }

    private static Map<String, String> catvodExt(JsonObject o) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String name : new String[]{"filters", "filter", "ext", "extend"}) {
            if (!o.has(name)) continue;
            var el = o.get(name);
            if (el.isJsonObject()) {
                el.getAsJsonObject().entrySet().forEach(e -> map.put(e.getKey(),
                        e.getValue().isJsonPrimitive() ? e.getValue().getAsString() : e.getValue().toString()));
            } else if (el.isJsonPrimitive()) {
                String raw = el.getAsString();
                if (raw != null && raw.trim().startsWith("{")) {
                    try {
                        JsonParser.parseString(raw).getAsJsonObject().entrySet().forEach(e -> map.put(e.getKey(),
                                e.getValue().isJsonPrimitive() ? e.getValue().getAsString() : e.getValue().toString()));
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        return map;
    }

    private static String first(String... values) {
        if (values == null) return "";
        for (String v : values) if (v != null && !v.isEmpty()) return v;
        return "";
    }

    private String handleConfig(Map<String, String> q, String body) throws Exception {
        String url = req(q, body, "url");
        if (url == null || url.isEmpty()) {
            JsonObject o = new JsonObject();
            o.addProperty("loaded", VodConfig.get().isLoaded());
            o.add("config", GsonHolder.GSON.toJsonTree(VodConfig.get().summary()));
            return ok(o);
        }
        VodConfig.get().load(url);
        BaseLoader.get().clear();
        // preload global spider jar (best effort)
        String spider = VodConfig.get().getSpider();
        if (!spider.isEmpty()) {
            try {
                BaseLoader.get().parseJar(spider, true);
            } catch (Exception e) {
                System.err.println("[gateway] preload spider failed: " + e.getMessage());
            }
        }
        return ok(GsonHolder.GSON.toJsonTree(VodConfig.get().summary()));
    }

    private String handleRpc(Map<String, String> q, String body) throws Exception {
        JsonObject req;
        if (body != null && !body.isBlank() && body.trim().startsWith("{")) {
            req = JsonParser.parseString(body).getAsJsonObject();
        } else {
            req = new JsonObject();
            q.forEach(req::addProperty);
        }
        String method = req.has("method") ? req.get("method").getAsString() : req(q, body, "method");
        String site = req.has("site") ? req.get("site").getAsString() : req(q, body, "site", "key");
        String data;
        switch (method == null ? "" : method) {
            case "home" -> data = SiteApi.homeContent(site);
            case "category" -> data = SiteApi.categoryContent(site,
                    str(req, "tid", "typeId"),
                    str(req, "pg", "page"),
                    req.has("filter") && req.get("filter").getAsJsonPrimitive().getAsBoolean(),
                    mapExt(req));
            case "detail" -> data = SiteApi.detailContent(site, str(req, "id"));
            case "search" -> data = SiteApi.searchContent(site,
                    str(req, "key", "word", "wd", "keyword"),
                    req.has("quick") && req.get("quick").getAsJsonPrimitive().getAsBoolean(),
                    str(req, "pg", "page"));
            case "player" -> data = SiteApi.playerContent(site, str(req, "flag"), str(req, "id"));
            case "config" -> {
                String url = str(req, "url");
                if (url != null && !url.isEmpty()) VodConfig.get().load(url);
                return ok(GsonHolder.GSON.toJsonTree(VodConfig.get().summary()));
            }
            default -> {
                return error("unknown method: " + method);
            }
        }
        return ok(single(data));
    }

    private String handleProxy(HttpExchange ex, Map<String, String> q, String body) throws Exception {
        Map<String, String> params = new HashMap<>(q);
        if (body != null && !body.isBlank()) {
            if (body.trim().startsWith("{")) {
                JsonObject o = JsonParser.parseString(body).getAsJsonObject();
                o.entrySet().forEach(e -> params.put(e.getKey(), e.getValue().isJsonPrimitive() ? e.getValue().getAsString() : e.getValue().toString()));
            } else {
                for (String pair : body.split("&")) {
                    int i = pair.indexOf('=');
                    if (i > 0) params.put(java.net.URLDecoder.decode(pair.substring(0, i), StandardCharsets.UTF_8),
                            java.net.URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8));
                }
            }
        }
        // Gateway-native raw forward (spider ProxyOrigin does not handle do=raw).
        String doMode = params.get("do");
        if ("raw".equals(doMode) || "file".equals(doMode) || "ts".equals(doMode)) {
            return handleRawProxy(ex, params);
        }
        Object[] result = BaseLoader.get().proxy(params);
        if (result == null || result.length < 3) return error("proxy empty");
        // TV spider contract: [status, contentType, InputStream, headers?]
        // legacy: [byte[], contentType, status]
        int code;
        String contentType;
        byte[] data;
        if (result[0] instanceof Integer) {
            code = (Integer) result[0];
            contentType = result[1] == null ? "application/octet-stream" : String.valueOf(result[1]);
            Object bodyObj = result[2];
            if (bodyObj instanceof java.io.InputStream in) {
                data = in.readAllBytes();
            } else if (bodyObj instanceof byte[] b) {
                data = b;
            } else if (bodyObj instanceof String s) {
                data = s.getBytes(StandardCharsets.UTF_8);
            } else {
                return error("proxy body unsupported: " + (bodyObj == null ? "null" : bodyObj.getClass().getName()));
            }
            if (result.length > 3 && result[3] instanceof Map<?, ?> headers) {
                for (Map.Entry<?, ?> e : headers.entrySet()) {
                    if (e.getKey() != null && e.getValue() != null) {
                        ex.getResponseHeaders().set(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
                    }
                }
            }
        } else {
            data = (byte[]) result[0];
            contentType = result[1] == null ? "application/octet-stream" : String.valueOf(result[1]);
            code = result[2] == null ? 200 : Integer.parseInt(String.valueOf(result[2]));
        }
        if (code < 100 || code > 599) code = 200;
        // Rewrite HLS playlists so segments go through this gateway (headers/auth).
        if (contentType != null && contentType.toLowerCase().contains("mpegurl")) {
            data = rewriteM3u8(data, params);
        } else if (data.length > 16 && data[0] == '#' && data[1] == 'E' && data[2] == 'X' && data[3] == 'T') {
            data = rewriteM3u8(data, params);
        }
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.sendResponseHeaders(code, data.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(data);
        }
        return null;
    }

    /** Forward url with optional headers= (base64 or k:v;k:v). Used for HLS segments. */
    private String handleRawProxy(HttpExchange ex, Map<String, String> params) throws Exception {
        String target = params.get("url");
        if (target == null || target.isEmpty()) return error("raw proxy missing url");
        okhttp3.Request.Builder rb = new okhttp3.Request.Builder().url(target);
        String headers = params.get("headers");
        if (headers != null && !headers.isEmpty()) {
            String decoded = headers;
            try {
                byte[] b = java.util.Base64.getDecoder().decode(headers.replaceAll("\\s", ""));
                decoded = new String(b, StandardCharsets.UTF_8);
            } catch (Exception ignored) {
            }
            // formats: "User-Agent:Lavf/57.83.100" or "ua=...;referer=..."
            for (String part : decoded.split("[;\\n]")) {
                int i = part.indexOf(':');
                if (i < 0) i = part.indexOf('=');
                if (i > 0) {
                    String k = part.substring(0, i).trim();
                    String v = part.substring(i + 1).trim();
                    if (!k.isEmpty() && !v.isEmpty()) rb.header(k, v);
                }
            }
        }
        if (rb.build().header("User-Agent") == null) {
            rb.header("User-Agent", "Mozilla/5.0");
        }
        try (okhttp3.Response res = com.github.catvod.net.OkHttp.client().newCall(rb.build()).execute()) {
            okhttp3.ResponseBody body = res.body();
            byte[] data = body == null ? new byte[0] : body.bytes();
            String ct = res.header("Content-Type");
            String path = target;
            int qi = path.indexOf('?');
            if (qi > 0) path = path.substring(0, qi);
            if (ct == null || ct.isEmpty() || ct.contains("text/html") || ct.contains("json")) {
                if (path.endsWith(".m3u8") || path.endsWith(".m3u")) ct = "application/vnd.apple.mpegURL";
                else if (path.endsWith(".ts")) ct = "video/mp2t";
                else if (path.endsWith(".aac")) ct = "audio/aac";
                else if (path.endsWith(".mp4")) ct = "video/mp4";
                else ct = "application/octet-stream";
            }
            int code = res.code();
            if (code < 100 || code > 599) code = 200;
            ex.getResponseHeaders().set("Content-Type", ct);
            ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            ex.sendResponseHeaders(code, data.length == 0 ? -1 : data.length);
            if (data.length > 0) {
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(data);
                }
            }
            return null;
        }
    }

    /** Point every segment URI back at /proxy?do=raw&url=... so UA/Referer headers apply. */
    private byte[] rewriteM3u8(byte[] data, Map<String, String> params) {
        try {
            String text = new String(data, StandardCharsets.UTF_8);
            String headers = params.getOrDefault("headers", "");
            String self = "http://127.0.0.1:" + port + "/proxy";
            StringBuilder out = new StringBuilder(text.length() + 256);
            String[] lines = text.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                String raw = lines[i];
                String line = raw.strip();
                if (line.isEmpty() || line.startsWith("#")) {
                    out.append(raw);
                } else {
                    String abs = line;
                    if (!line.startsWith("http://") && !line.startsWith("https://")) {
                        String base = params.get("url");
                        if (base != null && !base.isEmpty()) {
                            try {
                                abs = new java.net.URL(new java.net.URL(base), line).toString();
                            } catch (Exception ignored) {
                            }
                        }
                    }
                    out.append(self).append("?do=raw&url=")
                            .append(java.net.URLEncoder.encode(abs, StandardCharsets.UTF_8));
                    if (!headers.isEmpty()) {
                        out.append("&headers=").append(java.net.URLEncoder.encode(headers, StandardCharsets.UTF_8));
                    }
                }
                if (i < lines.length - 1) out.append('\n');
            }
            return out.toString().getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            return data;
        }
    }

    private JsonObject summary() {
        JsonObject o = new JsonObject();
        o.addProperty("ok", true);
        o.addProperty("version", "0.1.0");
        o.addProperty("loaded", VodConfig.get().isLoaded());
        GsonHolder.GSON.toJsonTree(VodConfig.get().summary()).getAsJsonObject().entrySet()
                .forEach(e -> o.add("config." + e.getKey(), e.getValue()));
        return o;
    }

    private java.util.List<Map<String, Object>> sites() {
        java.util.List<Map<String, Object>> list = new java.util.ArrayList<>();
        for (Site site : VodConfig.get().getSites()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", site.getKey());
            m.put("name", site.getName());
            m.put("type", site.getType());
            m.put("api", site.getApi());
            m.put("searchable", site.getSearchable());
            m.put("changeable", site.getChangeable());
            m.put("quickSearch", site.getQuickSearch());
            m.put("hide", site.getHide());
            m.put("indexs", site.getIndexs());
            m.put("timeout", site.getTimeout());
            list.add(m);
        }
        return list;
    }

    private String single(String json) {
        if (json == null || json.isEmpty()) return "{}";
        try {
            return json.trim();
        } catch (Exception e) {
            return "{\"raw\":" + GsonHolder.GSON.toJson(json) + "}";
        }
    }

    private boolean auth(HttpExchange ex) {
        if (token.isEmpty()) return true;
        String t = ex.getRequestHeaders().getFirst("X-Gateway-Token");
        if (t == null) t = query(ex).get("token");
        return token.equals(t);
    }

    private static Map<String, String> query(HttpExchange ex) {
        Map<String, String> map = new HashMap<>();
        String raw = ex.getRequestURI().getRawQuery();
        if (raw == null || raw.isEmpty()) return map;
        for (String pair : raw.split("&")) {
            int i = pair.indexOf('=');
            if (i > 0) {
                map.put(java.net.URLDecoder.decode(pair.substring(0, i), StandardCharsets.UTF_8),
                        java.net.URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8));
            } else if (!pair.isEmpty()) {
                map.put(java.net.URLDecoder.decode(pair, StandardCharsets.UTF_8), "");
            }
        }
        return map;
    }

    private static String readBody(HttpExchange ex) {
        try (InputStream in = ex.getRequestBody()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private static JsonObject bodyJson(String body) {
        if (body == null || body.isBlank()) return new JsonObject();
        try {
            String t = body.trim();
            if (t.startsWith("{")) return JsonParser.parseString(t).getAsJsonObject();
        } catch (Exception ignored) {
        }
        return new JsonObject();
    }

    private static String req(Map<String, String> q, String body, String... keys) {
        for (String k : keys) {
            String v = q.get(k);
            if (v != null && !v.isEmpty()) return v;
        }
        JsonObject o = bodyJson(body);
        for (String k : keys) {
            if (o.has(k) && o.get(k).isJsonPrimitive()) {
                String v = o.get(k).getAsString();
                if (v != null && !v.isEmpty()) return v;
            }
        }
        return "";
    }

    private static String req(Map<String, String> q, String body, String key) {
        return req(q, body, new String[]{key});
    }

    private static String req(Map<String, String> q, String body, String k1, String k2) {
        return req(q, body, new String[]{k1, k2});
    }

    private static boolean bool(Map<String, String> q, String body, String key, boolean def) {
        String v = req(q, body, key);
        if (v == null || v.isEmpty()) return def;
        return Boolean.parseBoolean(v) || "1".equals(v);
    }

    private static Map<String, String> ext(Map<String, String> q, String body) {
        Map<String, String> map = new HashMap<>();
        String raw = req(q, body, "ext", "extend");
        if (raw != null && !raw.isEmpty()) {
            if (raw.trim().startsWith("{")) {
                JsonObject o = JsonParser.parseString(raw).getAsJsonObject();
                o.entrySet().forEach(e -> map.put(e.getKey(), e.getValue().isJsonPrimitive() ? e.getValue().getAsString() : e.getValue().toString()));
            } else {
                map.put("ext", raw);
            }
        }
        // also accept ext.* query params
        q.forEach((k, v) -> {
            if (k.startsWith("ext.")) map.put(k.substring(4), v);
        });
        return map;
    }

    private static Map<String, String> mapExt(JsonObject req) {
        Map<String, String> map = new HashMap<>();
        if (req.has("ext") && req.get("ext").isJsonObject()) {
            req.getAsJsonObject("ext").entrySet()
                    .forEach(e -> map.put(e.getKey(), e.getValue().isJsonPrimitive() ? e.getValue().getAsString() : e.getValue().toString()));
        }
        return map;
    }

    private static String str(JsonObject req, String... keys) {
        for (String k : keys) {
            if (req.has(k) && req.get(k).isJsonPrimitive()) {
                String v = req.get(k).getAsString();
                if (v != null && !v.isEmpty()) return v;
            }
        }
        return "";
    }

    private static String ok(com.google.gson.JsonElement data) {
        JsonObject o = new JsonObject();
        o.addProperty("code", 0);
        o.add("data", data);
        return o.toString();
    }

    private static String ok(String data) {
        JsonObject o = new JsonObject();
        o.addProperty("code", 0);
        try {
            o.add("data", JsonParser.parseString(data));
        } catch (Exception e) {
            o.addProperty("data", data == null ? "" : data);
        }
        return o.toString();
    }

    private static String error(String message) {
        JsonObject o = new JsonObject();
        o.addProperty("code", 1);
        o.addProperty("message", message == null ? "error" : message);
        return o.toString();
    }

    private void sendJson(HttpExchange ex, int status, String body) throws IOException {
        if (body == null) return; // already handled (proxy)
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type,X-Gateway-Token");
            ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return;
        }
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
