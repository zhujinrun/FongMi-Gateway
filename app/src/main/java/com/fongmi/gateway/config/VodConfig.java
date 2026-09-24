package com.fongmi.gateway.config;

import com.fongmi.gateway.GsonHolder;
import com.fongmi.gateway.decode.Decoder;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.UriUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class VodConfig {

    private static final VodConfig INSTANCE = new VodConfig();

    private String url = "";
    private String spider = "";
    private String wallpaper = "";
    private String logo = "";
    private String notice = "";
    private String danmaku = "";
    private String assrt = "";
    private String rawJson = "";

    private List<Site> sites = new ArrayList<>();
    private List<Live> lives = new ArrayList<>();
    private List<String> flags = new ArrayList<>();
    private List<String> hosts = new ArrayList<>();
    private List<String> ads = new ArrayList<>();
    private List<JsonObject> rules = new ArrayList<>();
    private List<JsonObject> parses = new ArrayList<>();
    private List<JsonObject> doh = new ArrayList<>();
    private List<JsonObject> headers = new ArrayList<>();
    private List<JsonObject> proxy = new ArrayList<>();

    private Site home;
    private String error;

    public static VodConfig get() {
        return INSTANCE;
    }

    public synchronized void load(String configUrl) throws Exception {
        clear();
        this.url = configUrl == null ? "" : configUrl.trim();
        if (this.url.isEmpty()) throw new Exception("config url is empty");
        String json = Decoder.getJson(this.url, "VodConfig");
        applyJson(json);
    }

    public synchronized void loadJson(String json, String baseUrl) throws Exception {
        clear();
        this.url = baseUrl == null ? "" : baseUrl;
        applyJson(Decoder.verify(this.url.isEmpty() ? "http://localhost/" : this.url, json));
    }

    private void applyJson(String json) throws Exception {
        this.rawJson = json;
        JsonObject object = Json.parse(json).getAsJsonObject();
        if (object.has("msg")) throw new Exception(object.get("msg").getAsString());
        if (object.has("urls")) {
            throw new Exception("depot configs are not fully supported yet; provide a direct config url");
        }
        this.spider = Json.safeString(object, "spider");
        this.wallpaper = resolve(Json.safeString(object, "wallpaper"));
        this.logo = Json.safeString(object, "logo");
        this.notice = Json.safeString(object, "notice");
        this.danmaku = Json.safeString(object, "danmaku");
        this.assrt = Json.safeString(object, "assrt");
        this.flags = Json.safeListString(object, "flags");
        this.hosts = Json.safeListString(object, "hosts");
        this.ads = Json.safeListString(object, "ads");
        this.rules = objectList(object, "rules");
        this.parses = objectList(object, "parses");
        this.doh = objectList(object, "doh");
        this.headers = objectList(object, "headers");
        this.proxy = objectList(object, "proxy");

        this.sites = new ArrayList<>();
        for (JsonElement el : Json.safeListElement(object, "sites")) {
            Site site = Site.from(el, spider);
            if (!site.isEmpty()) sites.add(site);
        }

        this.lives = new ArrayList<>();
        for (JsonElement el : Json.safeListElement(object, "lives")) {
            lives.add(Live.from(el));
        }

        this.home = sites.isEmpty() ? null : sites.get(0);
        this.error = null;
    }

    private List<JsonObject> objectList(JsonObject object, String key) {
        List<JsonObject> list = new ArrayList<>();
        for (JsonElement el : Json.safeListElement(object, key)) {
            if (el.isJsonObject()) list.add(el.getAsJsonObject());
            else if (el.isJsonPrimitive()) {
                try {
                    String s = el.getAsString();
                    if (s.startsWith("http") || s.startsWith("./") || s.startsWith("../")) {
                        // external file reference - skip for MVP (fetchArray later)
                    } else if (Json.isObj(s)) {
                        list.add(Json.parse(s).getAsJsonObject());
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return list;
    }

    private String resolve(String value) {
        if (value == null || value.isEmpty() || value.startsWith("http") || value.startsWith("assets")) return value == null ? "" : value;
        if (url.isEmpty()) return value;
        return UriUtil.resolve(url, value);
    }

    public void clear() {
        url = "";
        spider = "";
        wallpaper = "";
        logo = "";
        notice = "";
        rawJson = "";
        sites = new ArrayList<>();
        lives = new ArrayList<>();
        flags = new ArrayList<>();
        hosts = new ArrayList<>();
        ads = new ArrayList<>();
        rules = new ArrayList<>();
        parses = new ArrayList<>();
        doh = new ArrayList<>();
        headers = new ArrayList<>();
        proxy = new ArrayList<>();
        home = null;
        error = null;
    }

    public boolean isLoaded() {
        return !sites.isEmpty();
    }

    public String getUrl() {
        return url;
    }

    public String getSpider() {
        return spider;
    }

    /** Override global spider jar for all sites that don't set their own jar. */
    public synchronized void setSpider(String value) {
        this.spider = value == null ? "" : value;
        for (Site s : sites) {
            s.setGlobalSpider(this.spider);
        }
    }

    public String getWallpaper() {
        return wallpaper;
    }

    public String getLogo() {
        return logo;
    }

    public String getNotice() {
        return notice;
    }

    public String getDanmaku() {
        return danmaku;
    }

    public String getAssrt() {
        return assrt;
    }

    public String getRawJson() {
        return rawJson;
    }

    public String getError() {
        return error;
    }

    public List<Site> getSites() {
        return sites == null ? Collections.emptyList() : sites;
    }

    public List<Live> getLives() {
        return lives == null ? Collections.emptyList() : lives;
    }

    public List<String> getFlags() {
        return flags == null ? Collections.emptyList() : flags;
    }

    public List<String> getHosts() {
        return hosts == null ? Collections.emptyList() : hosts;
    }

    public List<String> getAds() {
        return ads == null ? Collections.emptyList() : ads;
    }

    public List<JsonObject> getRules() {
        return rules == null ? Collections.emptyList() : rules;
    }

    public List<JsonObject> getParses() {
        return parses == null ? Collections.emptyList() : parses;
    }

    public List<JsonObject> getDoh() {
        return doh == null ? Collections.emptyList() : doh;
    }

    public List<JsonObject> getHeaders() {
        return headers == null ? Collections.emptyList() : headers;
    }

    public List<JsonObject> getProxy() {
        return proxy == null ? Collections.emptyList() : proxy;
    }

    public Site getHome() {
        return home;
    }

    public void setHome(Site site) {
        this.home = site;
    }

    public Site getSite(String key) {
        if (key == null) return new Site();
        for (Site site : getSites()) {
            if (key.equals(site.getKey())) return site;
        }
        return new Site();
    }

    public Map<String, Object> summary() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("url", url);
        map.put("spider", spider);
        map.put("siteCount", getSites().size());
        map.put("liveCount", getLives().size());
        map.put("parseCount", getParses().size());
        map.put("ruleCount", getRules().size());
        map.put("home", home == null ? "" : home.getKey());
        map.put("notice", notice);
        map.put("wallpaper", wallpaper);
        map.put("logo", logo);
        return map;
    }
}
