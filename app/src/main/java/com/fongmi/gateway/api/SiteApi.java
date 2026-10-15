package com.fongmi.gateway.api;

import com.fongmi.gateway.config.Site;
import com.fongmi.gateway.config.VodConfig;
import com.fongmi.gateway.loader.BaseLoader;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.crawler.SpiderNull;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Util;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;

public class SiteApi {

    /**
     * Build the drive-login QR URL the phone will scan. It must carry the gateway's LAN IP
     * (not 127.0.0.1) and the gateway's real port so the phone can reach /proxy?do=input.
     * Port matches the spider LoginShow QR convention (proxy input endpoint).
     */
    public static String loginQr() {
        String ip = lanIp();
        int port = com.github.catvod.Proxy.getPort();
        JsonObject o = new JsonObject();
        o.addProperty("url", "http://" + ip + ":" + port + "/proxy?do=input&csp=drive");
        o.addProperty("ip", ip);
        o.addProperty("port", port);
        return o.toString();
    }

    /**
     * Best-effort LAN IPv4 that a phone on the same network can actually reach.
     * Prefer the OS outbound source IP (default-route interface) via a UDP probe —
     * it skips virtual/NIC aliases that enumeration may hit first. Fall back to
     * enumerating site-local IPv4s. Returns 127.0.0.1 if all else fails.
     */
    private static String lanIp() {
        // 1) UDP probe: letting the kernel pick the source for a public peer yields the
        //    interface with the default route (the real LAN IP), without sending a packet.
        try (java.net.DatagramSocket s = new java.net.DatagramSocket()) {
            s.connect(java.net.InetAddress.getByName("8.8.8.8"), 80);
            java.net.InetAddress a = s.getLocalAddress();
            String ip = a == null ? null : a.getHostAddress();
            if (ip != null && isReachable(ip)) return ip;
        } catch (Throwable ignored) {
        }
        // 2) Fallback: first site-local IPv4 that is not loopback/link-local.
        try {
            java.util.Enumeration<java.net.NetworkInterface> ifs = java.net.NetworkInterface.getNetworkInterfaces();
            while (ifs != null && ifs.hasMoreElements()) {
                java.net.NetworkInterface nif = ifs.nextElement();
                if (!nif.isUp() || nif.isLoopback()) continue;
                java.util.Enumeration<java.net.InetAddress> addrs = nif.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    java.net.InetAddress a = addrs.nextElement();
                    if (a instanceof java.net.Inet4Address && isReachable(a.getHostAddress())) {
                        return a.getHostAddress();
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return "127.0.0.1";
    }

    /** True for a concrete IPv4 we can hand to the phone (not 0.0.0.0 / loopback / link-local). */
    private static boolean isReachable(String ip) {
        if (ip == null || ip.isEmpty()) return false;
        return !ip.startsWith("127.") && !ip.startsWith("169.254.") && !ip.equals("0.0.0.0");
    }

    public static String homeContent(String key) throws Exception {
        Site site = VodConfig.get().getSite(key);
        if (site.isEmpty()) throw new Exception("site not found: " + key);
        if (site.isSpider()) {
            Spider spider = spider(site);
            String home = spider.homeContent(true);
            String video = spider.homeVideoContent();
            JsonObject result = Json.isObj(home) ? JsonParser.parseString(home).getAsJsonObject() : new JsonObject();
            if (Json.isObj(video)) {
                JsonObject videoObj = JsonParser.parseString(video).getAsJsonObject();
                if (videoObj.has("list") && (result.get("list") == null || result.getAsJsonArray("list").isEmpty())) {
                    result.add("list", videoObj.get("list"));
                }
            }
            boolean hasList = result.has("list") && result.get("list").isJsonArray() && !result.getAsJsonArray("list").isEmpty();
            if (!hasList) {
                try {
                    String tid = "0";
                    if (result.has("class") && result.get("class").isJsonArray() && result.getAsJsonArray("class").size() > 0) {
                        JsonObject first = result.getAsJsonArray("class").get(0).getAsJsonObject();
                        if (first.has("type_id")) tid = first.get("type_id").getAsString();
                    }
                    String cat = categoryContent(key, tid, "1", true, Map.of());
                    if (Json.isObj(cat)) {
                        JsonObject catObj = JsonParser.parseString(cat).getAsJsonObject();
                        if (catObj.has("list") && catObj.get("list").isJsonArray() && !catObj.getAsJsonArray("list").isEmpty()) {
                            result.add("list", catObj.get("list"));
                        }
                    }
                } catch (Exception e) {
                    SpiderDebug.log(e);
                }
            }
            result.addProperty("key", site.getKey());
            result.addProperty("siteName", site.getName());
            return result.toString();
        }
        return httpGet(site, Map.of("filter", "true"));
    }

    public static String categoryContent(String key, String tid, String pg, boolean filter, Map<String, String> extend) throws Exception {
        Site site = VodConfig.get().getSite(key);
        if (site.isEmpty()) throw new Exception("site not found: " + key);
        if (site.isSpider()) {
            HashMap<String, String> ext = extend == null ? new HashMap<>() : new HashMap<>(extend);
            String category = spider(site).categoryContent(tid == null ? "" : tid, pg == null || pg.isEmpty() ? "1" : pg, filter, ext);
            return category == null || category.isEmpty() ? emptyList() : category;
        }
        Map<String, String> params = new HashMap<>();
        params.put("ac", site.getType() == 0 ? "videolist" : "detail");
        params.put("t", tid == null ? "" : tid);
        params.put("pg", pg == null || pg.isEmpty() ? "1" : pg);
        if (site.getType() == 1 && extend != null && !extend.isEmpty()) {
            params.put("f", com.fongmi.gateway.GsonHolder.GSON.toJson(extend));
        }
        if (filter) params.put("filter", "true");
        return httpGet(site, params);
    }

    public static String detailContent(String key, String id) throws Exception {
        Site site = VodConfig.get().getSite(key);
        if (site.isEmpty()) throw new Exception("site not found: " + key);
        if (site.isSpider()) {
            String detail = spider(site).detailContent(Arrays.asList(id));
            if (detail == null || detail.isEmpty()) return emptyVod();
            return filterDeadDriveLines(detail);
        }
        Map<String, String> params = new HashMap<>();
        params.put("ac", site.getType() == 0 ? "videolist" : "detail");
        params.put("ids", id);
        return httpGet(site, params);
    }

    public static String searchContent(String key, String keyword, boolean quick, String pg) throws Exception {
        Site site = VodConfig.get().getSite(key);
        if (site.isEmpty()) throw new Exception("site not found: " + key);
        if (!site.isSearchable()) throw new Exception("site not searchable");
        String page = pg == null || pg.isEmpty() ? "1" : pg;
        if (site.isSpider()) {
            Spider sp = spider(site);
            try {
                String result;
                if (!"1".equals(page)) result = sp.searchContent(keyword, quick, page);
                else result = sp.searchContent(keyword, quick);
                return result == null || result.isEmpty() ? emptyList() : result;
            } catch (RuntimeException e) {
                SpiderDebug.log(e);
                return emptyList();
            }
        }
        Map<String, String> params = new HashMap<>();
        params.put("wd", keyword == null ? "" : keyword);
        params.put("quick", String.valueOf(quick));
        params.put("extend", site.getExt());
        if (!"1".equals(page)) params.put("pg", page);
        return httpGet(site, params);
    }

    public static String playerContent(String key, String flag, String id) throws Exception {
        Site site = VodConfig.get().getSite(key);
        if (site.isEmpty()) throw new Exception("site not found: " + key);
        if (site.isSpider()) {
            String dead = deadDriveReason(flag);
            if (dead != null) throw new Exception("线路 " + flag + " " + dead + "，已跳过以免卡死");
            String player = spiderPlayWithTimeout(site, flag, id);
            JsonObject result;
            if (Json.isObj(player) && player != null && !player.isEmpty()) {
                result = JsonParser.parseString(player).getAsJsonObject();
            } else {
                result = new JsonObject();
            }
            if (!result.has("flag") || result.get("flag").isJsonNull() || result.get("flag").getAsString().isEmpty()) {
                result.addProperty("flag", flag == null ? "" : flag);
            }
            result.addProperty("key", key);
            if (site.getHeader() != null && !site.getHeader().isEmpty()) {
                JsonObject header = new JsonObject();
                site.getHeader().forEach(header::addProperty);
                if (!result.has("header") || result.get("header").isJsonNull()) result.add("header", header);
            }
            wrapPlayUrl(result);
            return result.toString();
        }
        // type 0/1: treat id as direct/play url; may need parse later
        JsonObject result = new JsonObject();
        result.addProperty("url", id);
        result.addProperty("key", key);
        result.addProperty("flag", flag == null ? "" : flag);
        result.addProperty("parse", Util.isVideoFormat(id) && site.getPlayUrl().isEmpty() ? 0 : 1);
        result.addProperty("playUrl", site.getPlayUrl());
        if (!site.getHeader().isEmpty()) {
            JsonObject header = new JsonObject();
            site.getHeader().forEach(header::addProperty);
            result.add("header", header);
        }
        return result.toString();
    }

    // ---- unlogged-drive protection --------------------------------------------------------
    // The spider's Cloud_uc/... playerContent retries with SystemClock.sleep() loops when the
    // drive is not logged in, blocking the request forever (Player sees a 120s axios timeout
    // and one HttpServer thread leaks per attempt). Two guards: hide dead lines up front, and
    // hard-timeout any spider play that still runs away.

    private static final long PLAY_TIMEOUT_MS = 30_000;
    private static final long PLAY_BUSY_TTL_MS = 10 * 60_000;
    private static final long PLAY_BUSY_SHORT_TTL_MS = 60_000;
    private static final ExecutorService PLAY_POOL = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "gateway-play");
        t.setDaemon(true);
        return t;
    });
    /** key|flag -> timestamp of the play that never came back */
    private static final ConcurrentHashMap<String, Long> PLAY_BUSY = new ConcurrentHashMap<>();
    /** drive prefix -> {loggedIn, enabled}, parsed from the drive panel labels; sticky while the panel is closed */
    private static volatile Map<String, boolean[]> DRIVE_STATE = Map.of();

    /**
     * Per-drive login/enable state from the spider login panel ("我的夸父- 已登录" / "启用中").
     * Live-parsed while the panel is open; keeps the last known state when it is closed.
     */
    private static Map<String, boolean[]> driveStates() {
        try {
            int n = android.app.AlertDialog.clickableCount();
            if (n > 0) {
                Map<String, boolean[]> m = new LinkedHashMap<>();
                for (int i = 0; i < n; i++) {
                    String l = String.valueOf(android.app.AlertDialog.clickableLabel(i));
                    int dash = l.lastIndexOf('-');
                    if (dash < 0) dash = l.lastIndexOf('－');
                    if (!l.contains("我的") || dash < 0) continue;
                    int start = l.indexOf("我的") + 2;
                    if (start >= dash) continue;
                    String name = l.substring(start, dash).trim();
                    if (name.isEmpty()) continue;
                    boolean loggedIn = l.contains("已登录");
                    boolean enabled = loggedIn;
                    if (i + 1 < n) {
                        String next = String.valueOf(android.app.AlertDialog.clickableLabel(i + 1));
                        if (next.contains("启用中")) enabled = true;
                        else if (next.contains("停用中")) enabled = false;
                    }
                    m.put(name, new boolean[]{loggedIn, enabled});
                }
                if (!m.isEmpty()) DRIVE_STATE = m;
            }
        } catch (Throwable ignored) {
        }
        return DRIVE_STATE;
    }

    /** Longest drive prefix matching this play line, or null. */
    private static boolean[] matchDrive(String line, Map<String, boolean[]> states) {
        if (line == null || line.isEmpty() || states.isEmpty()) return null;
        boolean[] best = null;
        int bestLen = 0;
        for (Map.Entry<String, boolean[]> e : states.entrySet()) {
            String name = e.getKey();
            if (line.startsWith(name) && name.length() > bestLen) {
                best = e.getValue();
                bestLen = name.length();
            }
        }
        return best;
    }

    /** Reason string when the line's drive is known to be not-logged-in / not-enabled, else null. */
    private static String deadDriveReason(String line) {
        boolean[] st = matchDrive(line, driveStates());
        if (st == null) return null;
        if (!st[0] && !st[1]) return "网盘未登录且未启用";
        if (!st[0]) return "网盘未登录";
        if (!st[1]) return "网盘未启用";
        return null;
    }

    /** Drop detail play lines whose drive is known dead, so the user cannot click them into a hang. */
    private static String filterDeadDriveLines(String json) {
        Map<String, boolean[]> states = driveStates();
        if (states.isEmpty()) return json;
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            JsonArray list = o.has("list") && o.get("list").isJsonArray() ? o.getAsJsonArray("list") : null;
            if (list == null) return json;
            for (JsonElement e : list) {
                if (!e.isJsonObject()) continue;
                JsonObject v = e.getAsJsonObject();
                if (!v.has("vod_play_from") || !v.get("vod_play_from").isJsonPrimitive()) continue;
                String from = v.get("vod_play_from").getAsString();
                String urls = v.has("vod_play_url") && v.get("vod_play_url").isJsonPrimitive() ? v.get("vod_play_url").getAsString() : "";
                String[] names = from.split("\\$\\$\\$");
                String[] urlParts = urls.split("\\$\\$\\$", -1);
                StringBuilder nf = new StringBuilder();
                StringBuilder nu = new StringBuilder();
                int kept = 0;
                for (int i = 0; i < names.length; i++) {
                    String nm = names[i].trim();
                    String reason = deadDriveReason(nm);
                    if (reason != null) {
                        System.err.println("[gateway] drive line hidden: " + nm + " (" + reason + ")");
                        continue;
                    }
                    if (kept > 0) {
                        nf.append("$$$");
                        nu.append("$$$");
                    }
                    nf.append(nm);
                    nu.append(i < urlParts.length ? urlParts[i] : "");
                    kept++;
                }
                v.addProperty("vod_play_from", nf.toString());
                v.addProperty("vod_play_url", nu.toString());
            }
            return o.toString();
        } catch (Throwable ignore) {
            return json;
        }
    }

    /**
     * Run the spider play off-thread with a hard timeout. On timeout the spider thread is
     * interrupted (its retry loop uses interruptible Thread.sleep) and the flag is marked busy
     * so repeat clicks fail instantly instead of leaking more stuck threads.
     */
    private static String spiderPlayWithTimeout(Site site, String flag, String id) throws Exception {
        String busyKey = site.getKey() + "|" + flag;
        Long since = PLAY_BUSY.get(busyKey);
        long ttl = matchDrive(flag, driveStates()) != null ? PLAY_BUSY_TTL_MS : PLAY_BUSY_SHORT_TTL_MS;
        if (since != null && System.currentTimeMillis() - since < ttl) {
            throw new Exception("线路 " + flag + " 上一次播放仍无响应（网盘可能未登录），请先在网盘设置里登录该网盘");
        }
        Future<String> future = PLAY_POOL.submit(() -> spider(site).playerContent(flag, id, VodConfig.get().getFlags()));
        try {
            String result = future.get(PLAY_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            PLAY_BUSY.remove(busyKey);
            return result;
        } catch (TimeoutException e) {
            future.cancel(true);
            PLAY_BUSY.put(busyKey, System.currentTimeMillis());
            System.err.println("[gateway] spider play timed out after " + PLAY_TIMEOUT_MS + "ms: site=" + site.getKey() + " flag=" + flag);
            throw new Exception("线路 " + flag + " 播放超时（网盘可能未登录），请先在网盘设置里登录该网盘");
        } catch (ExecutionException e) {
            Throwable c = e.getCause();
            if (c instanceof Exception) throw (Exception) c;
            throw e;
        }
    }

    /**
     * Make spider play output playable by strict clients:
     * 1) some spiders return `header` as a JSON string — parse it into an object;
     * 2) route media through this gateway's /proxy?do=raw when the response carries headers
     *    (so they are actually applied and every m3u8 segment keeps them), or when the url
     *    points at the spider's embedded file server (it emits both Content-Length and
     *    Transfer-Encoding: chunked plus a wrong Content-Length on Range, which Node rejects
     *    and browsers may reject or stall on).
     */
    private static void wrapPlayUrl(JsonObject result) {
        try {
            if (result.has("header")) {
                JsonElement h = result.get("header");
                if (h != null && h.isJsonPrimitive() && h.getAsJsonPrimitive().isString()) {
                    String raw = h.getAsString() == null ? "" : h.getAsString().trim();
                    if (raw.startsWith("{")) {
                        try {
                            result.add("header", JsonParser.parseString(raw).getAsJsonObject());
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
            if (!result.has("url") || !result.get("url").isJsonPrimitive()) return;
            String url = result.get("url").getAsString();
            if (url == null || url.isEmpty() || !url.startsWith("http")) return;
            String self = "http://127.0.0.1:" + com.github.catvod.Proxy.getPort() + "/proxy";
            if (url.startsWith(self + "?")) return;
            JsonObject header = null;
            if (result.has("header") && result.get("header").isJsonObject()) {
                header = result.getAsJsonObject("header");
            }
            boolean localProxy = url.matches("^http://(127\\.0\\.0\\.1|localhost):\\d+/proxy/.+");
            boolean hasHeader = header != null && header.size() > 0;
            if (!localProxy && !hasHeader) return;
            StringBuilder sb = new StringBuilder(self).append("?do=raw&url=")
                    .append(java.net.URLEncoder.encode(url, StandardCharsets.UTF_8));
            if (hasHeader) {
                sb.append("&headers=").append(java.net.URLEncoder.encode(header.toString(), StandardCharsets.UTF_8));
            }
            result.addProperty("url", sb.toString());
            System.err.println("[gateway] play url wrapped: localProxy=" + localProxy + " withHeader=" + hasHeader);
        } catch (Throwable t) {
            System.err.println("[gateway] wrap play url failed: " + t);
        }
    }

    public static String action(String key, String action) throws Exception {
        Site site = VodConfig.get().getSite(key);
        if (site.isEmpty()) throw new Exception("site not found: " + key);
        if (action == null || action.isEmpty()) throw new Exception("empty action");
        if (site.isSpider()) {
            Spider sp = spider(site);
            String result = sp.action(action);
            return result == null || result.isEmpty() ? "{}" : result;
        }
        Map<String, String> params = new HashMap<>();
        params.put("action", action);
        return httpGet(site, params);
    }

    public static String liveContent(String name, String url) throws Exception {
        for (var live : VodConfig.get().getLives()) {
            if (live.getName().equals(name) && !live.getApi().isEmpty()) {
                Site fake = new Site();
                // Use spider from lives via BaseLoader with api
                Spider spider = BaseLoader.get().getSpider("live_" + name, live.getApi(), live.getExt(), live.getJar());
                String content = spider.liveContent(url);
                return content == null ? "" : content;
            }
        }
        if (url != null && (url.startsWith("http") || url.startsWith("file"))) {
            if (url.startsWith("http")) return OkHttp.string(url);
            return com.github.catvod.utils.Path.read(new java.io.File(url.replace("file://", "")));
        }
        return "";
    }

    private static Spider spider(Site site) throws Exception {
        Spider spider = BaseLoader.get().getSpider(site);
        String err = BaseLoader.get().getError(site.getKey(), site.getJar());
        if (err != null && !err.isEmpty()) {
            site.setLoadFailed(true);
            throw new Exception(err);
        }
        if (spider instanceof SpiderNull) {
            if (site.isSpider()) {
                site.setLoadFailed(true);
                throw new Exception("spider null for " + site.getKey() + " api=" + site.getApi());
            }
        }
        return spider;
    }

    private static String httpGet(Site site, Map<String, String> params) throws Exception {
        String api = site.getApi();
        if (api == null || api.isEmpty() || !api.startsWith("http")) {
            throw new Exception("unsupported api for non-spider site: " + api);
        }
        HttpUrl base = HttpUrl.parse(api);
        if (base == null) throw new Exception("bad api url");
        HttpUrl.Builder builder = base.newBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            builder.addQueryParameter(e.getKey(), e.getValue() == null ? "" : e.getValue());
        }
        if (!site.getExt().isEmpty()) builder.addQueryParameter("extend", site.getExt());
        Request.Builder rb = new Request.Builder().url(builder.build());
        site.getHeader().forEach(rb::header);
        try (Response res = OkHttp.client().newCall(rb.build()).execute()) {
            if (res.body() == null) throw new Exception("empty response");
            String body = res.body().string();
            if (body.isEmpty()) throw new Exception("empty body");
            return body;
        }
    }

    private static String emptyList() {
        return "{\"page\":1,\"pagecount\":1,\"limit\":20,\"total\":0,\"list\":[],\"types\":[],\"filters\":{}}";
    }

    private static String emptyVod() {
        return "{\"list\":[]}";
    }
}
