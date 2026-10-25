package com.fongmi.gateway.server;

import com.fongmi.gateway.GsonHolder;
import com.fongmi.gateway.Main;
import com.fongmi.gateway.api.SiteApi;
import com.fongmi.gateway.config.Site;
import com.fongmi.gateway.config.VodConfig;
import com.fongmi.gateway.loader.BaseLoader;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;

public class GatewayServer {

    private final String host;
    private final int port;
    private final String token;
    private final String spiderFallback;
    private HttpServer server;

    public GatewayServer(String host, int port, String token) {
        this(host, port, token, "");
    }

    public GatewayServer(String host, int port, String token, String spiderFallback) {
        this.host = host;
        this.port = port;
        this.token = token == null ? "" : token;
        this.spiderFallback = spiderFallback == null ? "" : spiderFallback;
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

            // Block site-dependent requests until background preload (spider jars + config)
            // finishes, so first-launch /home and /category don't race startup and hit
            // "site not found". /health stays instant for probes and instance detection.
            if (!"/health".equals(path) && !VodConfig.get().isReady()) {
                if (!VodConfig.get().awaitReady(55_000)) {
                    System.err.println("[gateway] preload still running after 55s, continuing: " + path);
                }
            }

            String response;
            widenPool();
            if (isCatvodPath(path)) {
                response = handleCatvod(path, q, body);
                sendJson(ex, 200, response);
                return;
            }
            switch (path) {
                case "/health" -> response = ok(summary());
                case "/warm" -> response = ok(warm(req(q, body, "site", "key")));
                case "/config" -> response = handleConfig(q, body);
                case "/sites" -> response = ok(GsonHolder.GSON.toJsonTree(sites()));
                case "/home" -> response = ok(single(SiteApi.homeContent(req(q, body, "site", "key"))));
                case "/category" -> response = ok(single(SiteApi.categoryContent(
                        req(q, body, "site", "key"),
                        req(q, body, "tid", "typeId"),
                        req(q, body, "pg", "page"),
                        bool(q, body, "filter", true),
                        ext(q, body))));
                case "/detail" -> {
                    String did = req(q, body, "id", "ids");
                    if (did == null || did.isEmpty()) response = ok("{\"list\":[]}");
                    else response = ok(single(SiteApi.detailContent(req(q, body, "site", "key"), did)));
                }
                case "/search" -> response = ok(single(SiteApi.searchContent(
                        req(q, body, "site", "key"),
                        req(q, body, "key", "word", "wd", "keyword"),
                        bool(q, body, "quick", false),
                        req(q, body, "pg", "page"))));
                case "/player" -> response = ok(single(SiteApi.playerContent(
                        req(q, body, "site", "key"),
                        req(q, body, "flag"),
                        req(q, body, "id", "url"))));
                case "/action" -> response = ok(single(SiteApi.action(
                        req(q, body, "site", "key"),
                        req(q, body, "action"))));
                case "/ui" -> response = ok(uiState());
                case "/ui/qr" -> response = ok(qrPng(req(q, body, "name")));
                case "/ui/dismiss" -> {
                    android.app.Dialog.dismissAllShown();
                    response = ok(uiState());
                }
                case "/ui/click" -> {
                    int idx = 0;
                    try { idx = Integer.parseInt(req(q, body, "i", "index")); } catch (Exception ignore) {}
                    int retry = 0;
                    try { retry = Integer.parseInt(req(q, body, "retry")); } catch (Exception ignore) {}
                    if (retry < 0) retry = 0;
                    if (retry > 3) retry = 3;
                    int refresh = 0;
                    try { refresh = Integer.parseInt(req(q, body, "refresh")); } catch (Exception ignore) {}
                    widenPool();
                    long t0 = System.currentTimeMillis();
                    JsonObject before = diag();
                    for (int attempt = 0; attempt <= retry; attempt++) {
                        // spider 一个网盘只生成一次授权码：QR 会话持有锁并保持到扫码完成
                        android.app.Dialog.dismissAllShown();
                        resetQrSession();
                        // 授权码对话框会清空可点列表时才需要重新出一次登录面板
                        if (android.app.AlertDialog.clickableCount() == 0) {
                            SiteApi.action(req(q, body, "site", "key"), "LoginShow");
                            long deadline = System.currentTimeMillis() + 8000;
                            while (android.app.AlertDialog.clickableCount() == 0 && System.currentTimeMillis() < deadline) {
                                try { Thread.sleep(100); } catch (InterruptedException ignore) {}
                            }
                        }
                        System.err.println("[ui] click prep: attempt=" + attempt
                                + " count=" + android.app.AlertDialog.clickableCount());
                        android.app.AlertDialog.click(idx);
                        try { Thread.sleep(1500); } catch (InterruptedException ignore) {}
                        if (retry == 0 || hasNewQr(t0)) break;
                        System.err.println("[ui] click retry " + attempt + ": no new qr");
                    }
                    // 启停按钮改的是 spider 内部状态，面板文案要重新 LoginShow 才会刷新
                    if (refresh > 0) {
                        SiteApi.action(req(q, body, "site", "key"), "LoginShow");
                        long deadline = System.currentTimeMillis() + 4000;
                        while (android.app.AlertDialog.clickableCount() == 0 && System.currentTimeMillis() < deadline) {
                            try { Thread.sleep(100); } catch (InterruptedException ignore) {}
                        }
                        try { Thread.sleep(200); } catch (InterruptedException ignore) {}
                    }
                    JsonObject st = uiState();
                    st.add("before", before);
                    response = ok(st);
                }
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
            sendJson(ex, 500, error(describe(e)));
        } finally {
            long ms = System.currentTimeMillis() - start;
            if (ms > 500) System.err.println("[gateway] slow " + ms + "ms " + ex.getRequestURI());
        }
    }

    private static boolean isCatvodPath(String path) {
        if (path == null) return false;
        String[] p = path.split("/");
        return p.length == 3 && !p[1].isEmpty() && switch (p[2]) {
            case "init", "home", "category", "detail", "search", "play", "action" -> true;
            default -> false;
        };
    }

    /** Player catvod[type8]: POST /{siteKey}/{init|home|category|detail|search|play} 鈫?raw TVBox JSON (no envelope). */
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
                    yield clampCategoryPage(SiteApi.categoryContent(key, tid, pg, filter, ext), pg);
                }
                case "detail" -> {
                    String id = first(q.get("id"), q.get("ids"), str(o, "id", "ids"));
                    if (id == null || id.isEmpty()) yield "{\"list\":[]}";
                    yield SiteApi.detailContent(key, id);
                }
                case "search" -> SiteApi.searchContent(key,
                        first(q.get("wd"), q.get("key"), q.get("word"), str(o, "wd", "key", "word", "keyword")),
                        bool(q, body, "quick", false),
                        first(q.get("pg"), q.get("page"), str(o, "pg", "page")));
                case "play" -> SiteApi.playerContent(key,
                        first(q.get("flag"), str(o, "flag")),
                        first(q.get("id"), q.get("url"), str(o, "id", "url")));
                case "action" -> SiteApi.action(key,
                        first(q.get("action"), str(o, "action")));
                default -> throw new Exception("unknown catvod method: " + method);
            };
        } catch (Exception e) {
            System.err.println("[gateway] catvod " + method + " site=" + key + " failed: " + e.getMessage());
            throw e;
        }
    }

    /** 站源对越界页常返回兜底数据（同一项换个页码当新 id），会让 Player 无限翻页并重复渲染 */
    private static String clampCategoryPage(String json, String pg) {
        try {
            int page = Integer.parseInt(pg == null || pg.isEmpty() ? "1" : pg);
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            if (!o.has("pagecount") || !o.get("pagecount").isJsonPrimitive()) return json;
            int pagecount = o.get("pagecount").getAsInt();
            if (pagecount <= 0 || page <= pagecount || !o.has("list")) return json;
            o.add("list", new JsonArray());
            System.err.println("[gateway] category clamped: pg=" + page + " > pagecount=" + pagecount);
            return o.toString();
        } catch (Throwable ignore) {
            return json;
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

        String prevUrl = VodConfig.get().getUrl();
        String prevSpider = VodConfig.get().getSpider();
        String prevRaw = VodConfig.get().getRawJson();
        boolean prevLoaded = VodConfig.get().isLoaded();

        try {
            VodConfig.get().load(url);
        } catch (Exception loadErr) {
            restoreConfig(prevUrl, prevSpider, prevRaw, prevLoaded);
            throw loadErr;
        }

        String spider = VodConfig.get().getSpider();
        String spiderErr = preloadSpider(spider);
        if (spiderErr.isEmpty()) {
            Main.reportSpiderCoverage();
            return ok(GsonHolder.GSON.toJsonTree(VodConfig.get().summary()));
        }

        // Config JSON loaded OK 鈥?keep NEW sites. Only try to fix the spider jar.
        String[] candidates = {spiderFallback, prevSpider};
        for (String cand : candidates) {
            if (cand == null || cand.isEmpty() || cand.equals(spider)) continue;
            if (isAndroidOnlyErr(spiderErr)) {
                System.out.println("[gateway] config spider is Android-only (DexClassLoader/native), trying desktop pool: " + cand);
            } else {
                System.err.println("[gateway] config spider failed, trying fallback: " + cand);
            }
            if (preloadSpider(cand).isEmpty()) {
                VodConfig.get().setSpider(cand);
                System.out.println("[gateway] using fallback spider: " + cand);
                Main.reportSpiderCoverage();
                return ok(GsonHolder.GSON.toJsonTree(VodConfig.get().summary()));
            }
        }

        // No runnable jar: still return config so Player sync /sites works.
        System.err.println("[gateway] no runnable spider jar; keeping new config (csp home will fail until --spider is set)");
        JsonObject summary = GsonHolder.GSON.toJsonTree(VodConfig.get().summary()).getAsJsonObject();
        summary.addProperty("spiderWarning", "spider jar not runnable on JVM; pass --spider <compatible.jar>");
        return ok(summary);
    }

    /** Loads the jar. Returns "" on success, otherwise the load error text. */
    private String preloadSpider(String spider) {
        if (spider == null || spider.isEmpty()) return "";
        BaseLoader.get().clear();
        try {
            BaseLoader.get().parseJar(spider, true);
            String err = BaseLoader.get().getError("", spider);
            if (err != null && !err.isEmpty()) {
                if (isAndroidOnlyErr(err)) {
                    // Android-guard jar (DexClassLoader + .so): expected on desktop, not a fault.
                    System.out.println("[gateway] spider is Android-only (DexClassLoader/native), desktop cannot run it");
                } else {
                    System.err.println("[gateway] spider not runnable: " + err);
                }
                return err;
            }
            return "";
        } catch (Exception e) {
            System.err.println("[gateway] preload spider failed: " + e.getMessage());
            return String.valueOf(e.getMessage());
        }
    }

    private static boolean isAndroidOnlyErr(String err) {
        return err != null && (err.contains("requires Android native/Dex") || err.contains("dalvik.system.DexClassLoader"));
    }

    private void restoreConfig(String url, String spider, String raw, boolean loaded) {
        try {
            if (loaded && raw != null && !raw.isEmpty()) {
                VodConfig.get().loadJson(raw, url == null || url.isEmpty() ? "http://localhost/" : url);
            }
            if (spider != null && !spider.isEmpty()) {
                preloadSpider(spider);
            }
        } catch (Exception e) {
            System.err.println("[gateway] restore previous config failed: " + e.getMessage());
        }
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
            case "action" -> data = SiteApi.action(site, str(req, "action"));
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

    /**
     * Forward url with optional headers= (JSON object | base64 | "k:v;k:v" | "k:v\\nk:v").
     * Used for drive media and HLS segments. Applies the client's Range and always re-emits
     * a single well-formed framing (Content-Length XOR Transfer-Encoding).
     */
    private String handleRawProxy(HttpExchange ex, Map<String, String> params) throws Exception {
        String target = params.get("url");
        if (target == null || target.isEmpty()) return error("raw proxy missing url");
        URL u;
        try {
            u = new URL(target);
        } catch (Exception e) {
            return error("raw proxy bad url: " + target);
        }
        String range = ex.getRequestHeaders().getFirst("Range");
        boolean head = "HEAD".equalsIgnoreCase(ex.getRequestMethod());

        // The spider's embedded file server answers with both Content-Length and
        // Transfer-Encoding: chunked (Node rejects it, browsers may stall) and with a
        // wrong Content-Length when Range is used — bridge it through a raw socket.
        String host = u.getHost() == null ? "" : u.getHost();
        String uPath = u.getPath() == null ? "" : u.getPath();
        if (("127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host)) && uPath.startsWith("/proxy/")) {
            return streamLocalSpider(ex, u, params, range, head);
        }

        okhttp3.Request.Builder rb = new okhttp3.Request.Builder().url(target);
        applyHeaderParam(rb, params.get("headers"));
        if (rb.build().header("User-Agent") == null) {
            rb.header("User-Agent", "Mozilla/5.0");
        }
        if (range != null && !range.isBlank()) rb.header("Range", range);
        try (okhttp3.Response res = com.github.catvod.net.OkHttp.client().newCall(rb.build()).execute()) {
            okhttp3.ResponseBody body = res.body();
            byte[] data = body == null ? new byte[0] : body.bytes();
            int code = res.code();
            if (code < 100 || code > 599) code = 200;
            String ct = normalizeContentType(res.header("Content-Type"), uPath);
            if (isPlaylist(ct, uPath)) data = rewriteM3u8(data, params);
            String cr = res.header("Content-Range");
            boolean clientRange = range != null && !range.isBlank();
            if (cr != null) {
                if (clientRange) code = 206;   // server that answers 200 + Content-Range
                else cr = null;                // meaningless on a plain 200
            }
            ex.getResponseHeaders().set("Content-Type", ct);
            ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            if (cr != null) ex.getResponseHeaders().set("Content-Range", cr);
            if (head) {
                ex.sendResponseHeaders(code, -1);
                ex.close();
                return null;
            }
            ex.sendResponseHeaders(code, data.length == 0 ? -1 : data.length);
            if (data.length > 0) {
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(data);
                }
            }
            return null;
        }
    }

    /** headers= param: JSON object (preferred) | base64 | "k:v;k:v" / "k:v\\nk:v". */
    private static void applyHeaderParam(okhttp3.Request.Builder rb, String raw) {
        if (raw == null || raw.isEmpty()) return;
        String decoded = raw.trim();
        if (decoded.startsWith("{")) {
            try {
                JsonObject o = JsonParser.parseString(decoded).getAsJsonObject();
                for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                    if (e.getValue() != null && e.getValue().isJsonPrimitive()) {
                        try {
                            rb.header(e.getKey(), e.getValue().getAsString());
                        } catch (Exception ignored) {
                        }
                    }
                }
                return;
            } catch (Exception ignored) {
            }
        }
        try {
            byte[] b = java.util.Base64.getDecoder().decode(decoded.replaceAll("\\s", ""));
            decoded = new String(b, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
        String sep = decoded.contains("\n") ? "\n" : ";";
        for (String part : decoded.split(sep)) {
            int i = part.indexOf(':');
            if (i < 0) i = part.indexOf('=');
            if (i > 0) {
                String k = part.substring(0, i).trim();
                String v = part.substring(i + 1).trim();
                if (!k.isEmpty() && !v.isEmpty()) {
                    try {
                        rb.header(k, v);
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }

    /**
     * Bridge to the spider's embedded file server (ProxyOrigin local server).
     * It sends invalid dual framing, replies 200 + Content-Range for bytes=0-*,
     * reports a bogus Content-Length when Range is used, and drops the socket when
     * the request says Connection: close. Read it raw, then re-emit cleanly.
     */
    private String streamLocalSpider(HttpExchange ex, URL u, Map<String, String> params, String range, boolean head) throws Exception {
        int port = u.getPort() > 0 ? u.getPort() : 80;
        Socket sock = new Socket();
        try {
            sock.connect(new InetSocketAddress(u.getHost(), port), 5000);
            sock.setSoTimeout(20000);
            StringBuilder req = new StringBuilder();
            req.append("GET ").append(wirePath(u.getFile())).append(" HTTP/1.1\r\n");
            req.append("Host: ").append(u.getHost()).append(':').append(port).append("\r\n");
            req.append("Accept: */*\r\n");
            req.append("Connection: keep-alive\r\n");
            String ua = ex.getRequestHeaders().getFirst("User-Agent");
            req.append("User-Agent: ").append(safeHeader(ua == null ? "Mozilla/5.0" : ua)).append("\r\n");
            String ref = ex.getRequestHeaders().getFirst("Referer");
            if (ref != null && !ref.isBlank()) req.append("Referer: ").append(safeHeader(ref)).append("\r\n");
            if (range != null && !range.isBlank()) req.append("Range: ").append(safeHeader(range)).append("\r\n");
            req.append("\r\n");
            OutputStream out = sock.getOutputStream();
            out.write(req.toString().getBytes(StandardCharsets.ISO_8859_1));
            out.flush();

            InputStream in = new BufferedInputStream(sock.getInputStream());
            String block = readHeaderBlock(in);
            if (block == null || block.isEmpty()) return error("spider local proxy: empty response");
            String[] lines = block.split("\r\n");
            int status = 502;
            if (lines.length > 0) {
                String[] parts = lines[0].split(" ");
                if (parts.length >= 2) {
                    try {
                        status = Integer.parseInt(parts[1].trim());
                    } catch (Exception ignored) {
                    }
                }
            }
            Map<String, String> hs = new HashMap<>();
            for (int i = 1; i < lines.length; i++) {
                int c = lines[i].indexOf(':');
                if (c > 0) hs.put(lines[i].substring(0, c).trim().toLowerCase(), lines[i].substring(c + 1).trim());
            }
            boolean chunked = hs.getOrDefault("transfer-encoding", "").toLowerCase().contains("chunked");
            long upstreamLen = -1;
            try {
                if (hs.containsKey("content-length")) upstreamLen = Long.parseLong(hs.get("content-length").trim());
            } catch (Exception ignored) {
            }
            String contentRange = hs.get("content-range");
            long fixed = -1;
            if (contentRange != null) fixed = contentRangeLength(contentRange);
            else if (!chunked && upstreamLen > 0) fixed = upstreamLen;

            String ct = normalizeContentType(hs.get("content-type"), u.getPath());
            // 6678 answers 200 + Content-Range even when no Range was asked (meaningless per
            // RFC), and 200 for bytes=0-* (a client would treat that as the whole file).
            // Normalise: Range asked + Content-Range present -> 206, else drop Content-Range.
            if (contentRange != null) {
                if (range != null && !range.isBlank()) status = 206;
                else contentRange = null;
            }
            if (isPlaylist(ct, u.getPath())) {
                byte[] data = readPayload(in, chunked, fixed > 0 ? fixed : upstreamLen);
                data = rewriteM3u8(data, params);
                ex.getResponseHeaders().set("Content-Type", ct);
                ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                if (status == 204 || status == 304) {
                    ex.sendResponseHeaders(status, -1);
                } else {
                    ex.sendResponseHeaders(status, data.length == 0 ? -1 : data.length);
                }
                if (data.length > 0) {
                    try (OutputStream o = ex.getResponseBody()) {
                        o.write(data);
                    }
                }
                return null;
            }

            ex.getResponseHeaders().set("Content-Type", ct);
            ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            if (contentRange != null) ex.getResponseHeaders().set("Content-Range", contentRange);
            ex.getResponseHeaders().set("Accept-Ranges", "bytes");
            if (head || status == 204 || status == 304) {
                ex.sendResponseHeaders(status, -1);
                ex.close();
                return null;
            }
            ex.sendResponseHeaders(status, fixed > 0 ? fixed : 0);
            try (OutputStream o = ex.getResponseBody()) {
                if (chunked) copyChunked(in, o, fixed > 0 ? fixed : -1);
                else if (fixed > 0) copyFixed(in, o, fixed);
                else copyUntilEof(in, o);
            }
            return null;
        } finally {
            try {
                sock.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static String readHeaderBlock(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        while (sb.length() < 16384) {
            int b = in.read();
            if (b < 0) break;
            sb.append((char) b);
            int n = sb.length();
            if (n >= 4 && sb.charAt(n - 4) == '\r' && sb.charAt(n - 3) == '\n'
                    && sb.charAt(n - 2) == '\r' && sb.charAt(n - 1) == '\n') {
                return sb.substring(0, n - 4);
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    private static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int b;
        while ((b = in.read()) >= 0) {
            if (b == '\n') {
                int n = sb.length();
                if (n > 0 && sb.charAt(n - 1) == '\r') sb.setLength(n - 1);
                return sb.toString();
            }
            sb.append((char) b);
            if (sb.length() > 4096) return sb.toString();
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    private static void copyFixed(InputStream in, OutputStream out, long total) throws IOException {
        byte[] buf = new byte[65536];
        long left = total;
        while (left > 0) {
            int n = in.read(buf, 0, (int) Math.min(buf.length, left));
            if (n < 0) break;
            out.write(buf, 0, n);
            left -= n;
        }
        out.flush();
    }

    private static void copyUntilEof(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
        out.flush();
    }

    /** Decode chunked framing so only payload bytes go out; stop at limit when >= 0. */
    private static void copyChunked(InputStream in, OutputStream out, long limit) throws IOException {
        long written = 0;
        while (limit < 0 || written < limit) {
            String line = readLine(in);
            if (line == null) break;
            line = line.trim();
            int sc = line.indexOf(';');
            if (sc >= 0) line = line.substring(0, sc).trim();
            long size;
            try {
                size = Long.parseLong(line, 16);
            } catch (Exception e) {
                break;
            }
            if (size <= 0) break;
            long want = size;
            if (limit >= 0) want = Math.min(want, limit - written);
            copyFixed(in, out, want);
            written += want;
            if (want < size) break;
            readLine(in); // CRLF after the chunk
        }
        out.flush();
    }

    private static byte[] readPayload(InputStream in, boolean chunked, long expect) throws IOException {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        if (chunked) copyChunked(in, bos, -1);
        else if (expect > 0) copyFixed(in, bos, expect);
        else copyUntilEof(in, bos);
        return bos.toByteArray();
    }

    /** "bytes 1000-2000/47654277" -> 1001 */
    private static long contentRangeLength(String cr) {
        try {
            String spec = cr.trim();
            int sp = spec.indexOf(' ');
            if (sp >= 0) spec = spec.substring(sp + 1).trim();
            int sl = spec.indexOf('/');
            if (sl >= 0) spec = spec.substring(0, sl);
            String[] b = spec.split("-");
            if (b.length == 2) return Long.parseLong(b[1].trim()) - Long.parseLong(b[0].trim()) + 1;
        } catch (Exception ignored) {
        }
        return -1;
    }

    /** Guess media type from the path when upstream sends none/useless ones (e.g. "oct-stream"). */
    private static String normalizeContentType(String ct, String path) {
        String p = path == null ? "" : path;
        int qi = p.indexOf('?');
        if (qi > 0) p = p.substring(0, qi);
        p = p.toLowerCase();
        if (p.endsWith(".m3u8") || p.endsWith(".m3u")) return "application/vnd.apple.mpegURL";
        if (p.endsWith(".ts")) return "video/mp2t";
        if (p.endsWith(".mp4")) return "video/mp4";
        if (p.endsWith(".flv")) return "video/x-flv";
        if (p.endsWith(".mkv")) return "video/x-matroska";
        if (p.endsWith(".mov")) return "video/quicktime";
        if (p.endsWith(".aac")) return "audio/aac";
        if (p.endsWith(".mp3")) return "audio/mpeg";
        if (p.endsWith(".m4a")) return "audio/mp4";
        String lower = ct == null ? "" : ct.toLowerCase();
        if (!lower.isEmpty() && !lower.contains("oct-stream") && !lower.contains("text/html")) return ct;
        return "application/octet-stream";
    }

    private static boolean isPlaylist(String ct, String path) {
        String lower = ct == null ? "" : ct.toLowerCase();
        if (lower.contains("mpegurl")) return true;
        String p = path == null ? "" : path;
        int qi = p.indexOf('?');
        if (qi > 0) p = p.substring(0, qi);
        p = p.toLowerCase();
        return p.endsWith(".m3u8") || p.endsWith(".m3u");
    }

    private static String safeHeader(String v) {
        return v == null ? "" : v.replaceAll("[\\r\\n]", " ");
    }

    /** Percent-encode anything not wire-safe so the request line survives ISO-8859-1. */
    private static String wirePath(String file) {
        String p = file == null || file.isEmpty() ? "/" : file;
        StringBuilder sb = new StringBuilder(p.length() + 8);
        for (byte b : p.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xFF;
            if (c <= 0x20 || c >= 0x7F) {
                sb.append('%').append(String.format("%02X", c));
            } else {
                sb.append((char) c);
            }
        }
        return sb.toString();
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
        o.addProperty("ready", VodConfig.get().isReady());
        GsonHolder.GSON.toJsonTree(VodConfig.get().summary()).getAsJsonObject().entrySet()
                .forEach(e -> o.add("config." + e.getKey(), e.getValue()));
        int[] cov = cspCoverage();
        o.addProperty("siteOk", cov[0]);
        o.addProperty("siteMissing", cov[1]);
        return o;
    }

    /**
     * [可运行源数, 跑不了的源数]。桌面网关只能加载纯 JVM 爬虫 jar；安卓专属壳
     * （DexClassLoader + .so，典型是王二小新版配置）里的类在池里解析不到。
     * 前端据此把源标成「不支持」，而不是等一次注定失败的 /home 再显示「无数据」。
     */
    private static int[] cspCoverage() {
        int ok = 0;
        int missing = 0;
        try {
            for (Site site : VodConfig.get().getSites()) {
                String api = site.getApi();
                if (api == null || !api.startsWith("csp_")) continue;
                if (BaseLoader.get().hasSpider(api)) ok++;
                else missing++;
            }
        } catch (Throwable ignored) {
        }
        return new int[]{ok, missing};
    }

    /**
     * Warm a spider source's first homeContent off the request path. The first call pays a
     * one-time cost (domain probing + first CMS fetch) that would otherwise hit the user's
     * first click. With no site param, warm the first spider source as a fallback.
     * Returns immediately; the warm runs on a daemon thread and fills the result cache so
     * the next real /home or /category returns instantly.
     */
    private JsonObject warm(String siteKey) {
        JsonObject o = new JsonObject();
        try {
            Site target = null;
            if (siteKey != null && !siteKey.isEmpty()) {
                target = VodConfig.get().getSite(siteKey);
                if (target.isEmpty()) target = null;
            }
            if (target == null) {
                for (Site site : VodConfig.get().getSites()) {
                    if (site.isSpider() && site.getApi().startsWith("csp_")) {
                        target = site;
                        break;
                    }
                }
            }
            if (target == null) {
                o.addProperty("ok", false);
                o.addProperty("reason", "no spider site");
                return o;
            }
            final Site t = target;
            o.addProperty("ok", true);
            o.addProperty("key", t.getKey());
            o.addProperty("warming", true);
            Thread th = new Thread(() -> {
                try {
                    long t0 = System.currentTimeMillis();
                    System.out.println("[gateway] warm: " + t.getKey() + " (" + t.getApi() + ")");
                    SiteApi.homeContent(t.getKey());
                    System.out.println("[gateway] warmed " + t.getKey() + " in " + (System.currentTimeMillis() - t0) + "ms");
                } catch (Throwable e) {
                    System.err.println("[gateway] warm failed (ignored): " + e);
                }
            }, "gateway-warm");
            th.setDaemon(true);
            th.start();
        } catch (Throwable t) {
            o.addProperty("ok", false);
            o.addProperty("reason", String.valueOf(t));
        }
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
            // 本机能否真的跑起来（安卓专属爬虫 / 池里缺类 都为 false），前端据此禁用并标「不支持」
            m.put("supported", BaseLoader.get().hasSpider(site.getApi()));
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
        // Spider LoginShow QR points a phone browser at /proxy?do=input (and its cookie push,
        // /proxy?do=<drive>&type=input). Those can't attach X-Gateway-Token, so exempt only the
        // input flow; every other endpoint stays gated.
        if (isProxyInput(ex)) return true;
        String t = ex.getRequestHeaders().getFirst("X-Gateway-Token");
        if (t == null) t = query(ex).get("token");
        return token.equals(t);
    }

    private static boolean isProxyInput(HttpExchange ex) {
        if (!"/proxy".equals(ex.getRequestURI().getPath())) return false;
        Map<String, String> q = query(ex);
        return "input".equals(q.get("do")) || "input".equals(q.get("type"));
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

    private static JsonObject qrPng(String name) throws IOException {
        JsonObject o = new JsonObject();
        java.io.File dir = new java.io.File(System.getProperty("java.io.tmpdir"), "gwui");
        java.io.File[] fs = dir.listFiles((d, n) -> n != null && n.endsWith(".png"));
        if (fs == null || fs.length == 0) {
            o.addProperty("error", "no qr");
            return o;
        }
        java.util.Arrays.sort(fs, (x, y) -> Long.compare(x.lastModified(), y.lastModified()));
        java.io.File f = fs[fs.length - 1];
        if (name != null && !name.isEmpty()) {
            f = null;
            for (java.io.File c : fs) if (c.getName().equals(name)) f = c;
            if (f == null) {
                o.addProperty("error", "not found: " + name);
                return o;
            }
        }
        byte[] png = java.nio.file.Files.readAllBytes(f.toPath());
        int w = png.length > 24 ? ((png[16] & 0xff) << 24 | (png[17] & 0xff) << 16 | (png[18] & 0xff) << 8 | (png[19] & 0xff)) : 0;
        int h = png.length > 24 ? ((png[20] & 0xff) << 24 | (png[21] & 0xff) << 16 | (png[22] & 0xff) << 8 | (png[23] & 0xff)) : 0;
        o.addProperty("name", f.getName());
        o.addProperty("width", w);
        o.addProperty("height", h);
        o.addProperty("dataUrl", "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(png));
        return o;
    }

    private static JsonObject uiState() {
        JsonObject o = new JsonObject();
        int n = android.app.AlertDialog.clickableCount();
        JsonArray labels = new JsonArray();
        for (int i = 0; i < n; i++) labels.add(android.app.AlertDialog.clickableLabel(i));
        o.addProperty("count", n);
        o.add("labels", labels);
        JsonArray texts = new JsonArray();
        for (String t : android.app.AlertDialog.texts()) texts.add(t);
        o.add("texts", texts);
        o.add("qrs", qrList());
        o.add("diag", diag());
        return o;
    }

    private static Class<?> cls(String name) throws ClassNotFoundException {
        ClassLoader cl = android.view.View.spiderLoader;
        if (cl != null) {
            try { return Class.forName(name, true, cl); } catch (Throwable ignore) {}
        }
        return Class.forName(name);
    }

    private static final String[][] SESSIONS = {
        {"com.github.catvod.spider.merge.Z", "Zm", "quark"},   // 夸克
        {"com.github.catvod.spider.merge.xU", "s", "uc"},      // UC
        {"com.github.catvod.spider.merge.RQ", "xa", "douyin"}, // 抖音
        {"com.github.catvod.spider.merge.V", "v", "ali"},      // 阿里
    };

    private static Object session(String[] spec) {
        try {
            Class<?> c = cls(spec[0]);
            java.lang.reflect.Method m = c.getDeclaredMethod(spec[1]);
            m.setAccessible(true);
            return m.invoke(null);
        } catch (Throwable t) {
            return null;
        }
    }

    private static JsonObject diag() {
        JsonObject o = new JsonObject();
        try {
            Class<?> pan = cls("com.github.catvod.spider.Pan");
            Object panObj = android.view.View.spiderPan;
            for (String f : new String[]{"q", "B", "G", "G4", "v", "ee", "xa", "Gd", "g9"}) {
                try {
                    java.lang.reflect.Field fl = pan.getDeclaredField(f);
                    fl.setAccessible(true);
                    o.addProperty("pan." + f, String.valueOf(fl.get(null)));
                } catch (Throwable t) {
                    try {
                        java.lang.reflect.Field fl = pan.getDeclaredField(f);
                        fl.setAccessible(true);
                        o.addProperty("pan." + f, String.valueOf(fl.get(panObj)));
                    } catch (Throwable t2) {
                        o.addProperty("pan." + f, "err:" + t2.getClass().getSimpleName());
                    }
                }
            }
        } catch (Throwable t) {
            o.addProperty("pan", "err:" + t);
        }
        for (String[] spec : SESSIONS) {
            Object inst = session(spec);
            if (inst == null) {
                o.addProperty(spec[2], "err:no-singleton");
                continue;
            }
            StringBuilder sb = new StringBuilder();
            for (java.lang.reflect.Field f : inst.getClass().getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object val = f.get(inst);
                    if (val instanceof java.util.concurrent.locks.ReentrantLock) {
                        java.util.concurrent.locks.ReentrantLock l = (java.util.concurrent.locks.ReentrantLock) val;
                        sb.append(f.getName()).append("=lock:").append(l.isLocked() ? "held" : "free").append(' ');
                    } else if (val instanceof android.app.Dialog) {
                        sb.append(f.getName()).append("=dlg:").append(((android.app.Dialog) val).isShowing() ? "on" : "off").append(' ');
                    } else if (f.getType() == boolean.class) {
                        sb.append(f.getName()).append('=').append(val).append(' ');
                    }
                } catch (Throwable ignore) {
                }
            }
            o.addProperty(spec[2], sb.toString().trim());
        }
        o.addProperty("pool", poolState());
        o.addProperty("threads", threadState());
        return o;
    }

    private static Object spiderInit() throws Exception {
        Class<?> c = cls("com.github.catvod.spider.InitOrigin");
        return c.getMethod("get").invoke(null);
    }

    private static String poolState() {
        try {
            Class<?> c = cls("com.github.catvod.spider.InitOrigin");
            java.lang.reflect.Field f = c.getDeclaredField("yq");
            f.setAccessible(true);
            Object pool = f.get(spiderInit());
            if (pool instanceof java.util.concurrent.ThreadPoolExecutor) {
                java.util.concurrent.ThreadPoolExecutor tpe = (java.util.concurrent.ThreadPoolExecutor) pool;
                return "core=" + tpe.getCorePoolSize() + " max=" + tpe.getMaximumPoolSize()
                        + " active=" + tpe.getActiveCount() + " size=" + tpe.getPoolSize()
                        + " queue=" + tpe.getQueue().size();
            }
            return String.valueOf(pool);
        } catch (Throwable t) {
            return "err:" + t;
        }
    }

    private static String threadState() {
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (!t.getName().startsWith("pool-")) continue;
            if (n++ >= 8) { sb.append("... "); break; }
            sb.append(t.getName()).append(':').append(t.getState()).append(' ');
        }
        return n == 0 ? "none" : sb.toString().trim();
    }

    /** spider 用 newFixedThreadPool(5)，卡住的 QR 任务会把新任务全部排队 -> 换成不限线程的缓存池 */
    private static volatile boolean poolWidened = false;

    private static void widenPool() {
        if (poolWidened) return;
        try {
            Class<?> c = cls("com.github.catvod.spider.InitOrigin");
            java.lang.reflect.Field f = c.getDeclaredField("yq");
            f.setAccessible(true);
            Object io = spiderInit();
            Object pool = f.get(io);
            if (!(pool instanceof java.util.concurrent.ThreadPoolExecutor)) return;
            java.util.concurrent.ThreadPoolExecutor tpe = (java.util.concurrent.ThreadPoolExecutor) pool;
            String before = "core=" + tpe.getCorePoolSize() + " active=" + tpe.getActiveCount()
                    + " size=" + tpe.getPoolSize() + " queue=" + tpe.getQueue().size();
            if (tpe.getMaximumPoolSize() >= 64) { poolWidened = true; return; }
            f.set(io, Executors.newCachedThreadPool());
            poolWidened = true;
            System.err.println("[ui] widenPool: " + before + " -> cached");
        } catch (Throwable t) {
            System.err.println("[ui] widenPool fail " + t);
        }
    }

    /**
     * 注意别叫 str(Object,String)：Java 重载决议会优先匹配固定 arity 版本，
     * 导致 str(o, "flag") 走这里反射 JsonObject 的字段（永远失败返回 null），
     * POST body 的 flag/action/url 全部丢失。
     */
    private static String reflectField(Object o, String field) {
        try {
            java.lang.reflect.Field fl = o.getClass().getDeclaredField(field);
            fl.setAccessible(true);
            return String.valueOf(fl.get(o));
        } catch (Throwable t) {
            return null;
        }
    }

    /** 让 spider 允许同一个网盘再次生成授权码：清掉上一次 QR 会话的标志/对话框/锁 */
    private static void resetQrSession() {
        for (String[] spec : SESSIONS) {
            try {
                Object inst = session(spec);
                if (inst == null) continue;
                Class<?> c = inst.getClass();
                for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                    f.setAccessible(true);
                    if (android.app.Dialog.class.isAssignableFrom(f.getType())) {
                        Object d = f.get(inst);
                        if (d != null) {
                            try { ((android.app.Dialog) d).dismiss(); } catch (Throwable ignore) {}
                            try { f.set(inst, null); } catch (Throwable ignore) {}
                            System.err.println("[ui] resetQrSession: " + spec[2] + "." + f.getName() + " dialog cleared");
                        }
                    } else if (f.getType() == boolean.class && f.getName().equals("un")) {
                        f.setBoolean(inst, false);
                    }
                }
                for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                    if (f.getType() != java.util.concurrent.locks.ReentrantLock.class) continue;
                    f.setAccessible(true);
                    Object l = f.get(inst);
                    if (l instanceof java.util.concurrent.locks.ReentrantLock
                            && ((java.util.concurrent.locks.ReentrantLock) l).isLocked()) {
                        f.set(inst, new java.util.concurrent.locks.ReentrantLock(true));
                        System.err.println("[ui] resetQrSession: " + spec[2] + "." + f.getName() + " lock replaced");
                    }
                }
            } catch (Throwable t) {
                System.err.println("[ui] resetQrSession: " + spec[2] + " " + t);
            }
        }
        try {
            Class<?> pan = cls("com.github.catvod.spider.Pan");
            java.lang.reflect.Field fOw = pan.getDeclaredField("OW");
            fOw.setAccessible(true);
            Object ow = fOw.get(null);
            if (ow instanceof android.app.Dialog) ((android.app.Dialog) ow).dismiss();
            fOw.set(null, null);
        } catch (Throwable ignore) {
        }
    }

    private static boolean hasNewQr(long since) {
        for (com.google.gson.JsonElement e : qrList()) {
            try {
                if (e.getAsJsonObject().get("mtime").getAsLong() > since) return true;
            } catch (Throwable ignore) {
            }
        }
        return false;
    }

    private static JsonArray qrList() {
        JsonArray a = new JsonArray();
        try {
            java.io.File dir = new java.io.File(System.getProperty("java.io.tmpdir"), "gwui");
            java.io.File[] fs = dir.listFiles();
            if (fs == null) return a;
            java.util.Arrays.sort(fs, (x, y) -> Long.compare(x.lastModified(), y.lastModified()));
            for (java.io.File f : fs) {
                if (!f.getName().endsWith(".png")) continue;
                JsonObject e = new JsonObject();
                e.addProperty("name", f.getName());
                e.addProperty("path", f.getAbsolutePath());
                e.addProperty("size", f.length());
                e.addProperty("mtime", f.lastModified());
                a.add(e);
            }
        } catch (Throwable t) {
            JsonObject e = new JsonObject();
            e.addProperty("error", String.valueOf(t));
            a.add(e);
        }
        return a;
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

    private static String describe(Throwable e) {
        StringBuilder sb = new StringBuilder();
        Throwable t = e;
        int depth = 0;
        while (t != null && depth < 6) {
            if (sb.length() > 0) sb.append(" <- ");
            sb.append(t.getClass().getSimpleName()).append(": ").append(t.getMessage());
            t = t.getCause() == t ? null : t.getCause();
            depth++;
        }
        return sb.toString();
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
