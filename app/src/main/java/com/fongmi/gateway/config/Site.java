package com.fongmi.gateway.config;

import com.google.gson.JsonElement;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Site {

    @SerializedName("key")
    private String key = "";
    @SerializedName("name")
    private String name = "";
    @SerializedName("api")
    private String api = "";
    @SerializedName("ext")
    private String ext = "";
    @SerializedName("jar")
    private String jar = "";
    @SerializedName("type")
    private Integer type = 0;
    @SerializedName("hide")
    private Integer hide = 0;
    @SerializedName("indexs")
    private Integer indexs = 0;
    @SerializedName("timeout")
    private Integer timeout = 15;
    @SerializedName("searchable")
    private Integer searchable = 1;
    @SerializedName("changeable")
    private Integer changeable = 1;
    @SerializedName("quickSearch")
    private Integer quickSearch = 1;
    @SerializedName("click")
    private String click = "";
    @SerializedName("playUrl")
    private String playUrl = "";
    @SerializedName("categories")
    private List<String> categories = new ArrayList<>();
    @SerializedName("header")
    private Map<String, String> header = new HashMap<>();
    @SerializedName("style")
    private Map<String, Object> style = new HashMap<>();

    private transient String globalSpider = "";
    private transient boolean loadFailed;

    public static Site from(JsonElement element, String globalSpider) {
        Site site = new Site();
        try {
            JsonElement el = element;
            // Gson cannot map JSON object/array into String ext — stringify first
            if (element.isJsonObject()) {
                com.google.gson.JsonObject o = element.getAsJsonObject();
                if (o.has("ext") && !o.get("ext").isJsonPrimitive()) {
                    o = o.deepCopy();
                    o.addProperty("ext", o.get("ext").toString());
                    el = o;
                }
            }
            Site tmp = com.fongmi.gateway.GsonHolder.GSON.fromJson(el, Site.class);
            if (tmp != null) site = tmp;
        } catch (Exception e) {
            // keep defaults
        }
        site.globalSpider = globalSpider == null ? "" : globalSpider;
        if (site.type == null) site.type = 0;
        if (site.ext == null) site.ext = "";
        if (site.header == null) site.header = new HashMap<>();
        if (site.categories == null) site.categories = new ArrayList<>();
        // ext may be object/array in JSON - re-read raw
        if (element.isJsonObject() && element.getAsJsonObject().has("ext")) {
            JsonElement extEl = element.getAsJsonObject().get("ext");
            if (extEl.isJsonPrimitive()) site.ext = extEl.getAsString();
            else site.ext = extEl.toString();
        }
        if (element.isJsonObject() && element.getAsJsonObject().has("jar")) {
            JsonElement jarEl = element.getAsJsonObject().get("jar");
            if (jarEl.isJsonPrimitive()) site.jar = jarEl.getAsString();
        }
        return site;
    }

    public String getKey() {
        return key == null ? "" : key;
    }

    public String getName() {
        return name == null ? "" : name;
    }

    public String getApi() {
        return api == null ? "" : api;
    }

    public String getExt() {
        return ext == null ? "" : ext;
    }

    public void setExt(String ext) {
        this.ext = ext;
    }

    public String getJar() {
        if (jar != null && !jar.isEmpty()) return jar;
        return globalSpider;
    }

    public void setGlobalSpider(String spider) {
        this.globalSpider = spider == null ? "" : spider;
    }

    public Integer getType() {
        return type == null ? 0 : type;
    }

    public Integer getHide() {
        return hide == null ? 0 : hide;
    }

    public Integer getIndexs() {
        return indexs == null ? 0 : indexs;
    }

    public Integer getTimeout() {
        return timeout == null || timeout < 1 ? 15 : timeout;
    }

    public Integer getSearchable() {
        return searchable == null ? 1 : searchable;
    }

    public Integer getChangeable() {
        return changeable == null ? 1 : changeable;
    }

    public Integer getQuickSearch() {
        return quickSearch == null ? 1 : quickSearch;
    }

    public String getClick() {
        return click == null ? "" : click;
    }

    public String getPlayUrl() {
        return playUrl == null ? "" : playUrl;
    }

    public List<String> getCategories() {
        return categories == null ? new ArrayList<>() : categories;
    }

    public Map<String, String> getHeader() {
        return header == null ? new HashMap<>() : header;
    }

    public Map<String, Object> getStyle() {
        return style == null ? new HashMap<>() : style;
    }

    public boolean isSpider() {
        return getType() == 3;
    }

    public boolean isEmpty() {
        return getKey().isEmpty();
    }

    public boolean isLoadFailed() {
        return loadFailed;
    }

    public void setLoadFailed(boolean loadFailed) {
        this.loadFailed = loadFailed;
    }

    public boolean isSearchable() {
        return getSearchable() != 0;
    }
}
