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

            String response;
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
        Object[] result = BaseLoader.get().proxy(params);
        if (result == null || result.length < 3) return error("proxy empty");
        byte[] data = (byte[]) result[0];
        String contentType = result[1] == null ? "application/octet-stream" : String.valueOf(result[1]);
        int code = result[2] == null ? 200 : Integer.parseInt(String.valueOf(result[2]));
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.sendResponseHeaders(code, data.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(data);
        }
        return null;
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
