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
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;

public class SiteApi {

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
        if (site.isSpider()) {
            String detail = spider(site).detailContent(Arrays.asList(id));
            return detail == null || detail.isEmpty() ? emptyVod() : detail;
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
        if (site.isSpider()) {
            String player = spider(site).playerContent(flag, id, VodConfig.get().getFlags());
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
        String nativeErr = BaseLoader.get().getError("__jar_native__", "");
        if (nativeErr != null && !nativeErr.isEmpty()) {
            site.setLoadFailed(true);
            throw new Exception(nativeErr);
        }
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
