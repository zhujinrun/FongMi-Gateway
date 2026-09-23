package com.fongmi.gateway.config;

import com.fongmi.gateway.GsonHolder;
import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

public class Live {

    @SerializedName("name")
    private String name = "";
    @SerializedName("type")
    private Integer type = 0;
    @SerializedName("url")
    private String url = "";
    @SerializedName("api")
    private String api = "";
    @SerializedName("ext")
    private String ext = "";
    @SerializedName("jar")
    private String jar = "";
    @SerializedName("epg")
    private String epg = "";
    @SerializedName("logo")
    private String logo = "";
    @SerializedName("ua")
    private String ua = "";
    @SerializedName("referer")
    private String referer = "";
    @SerializedName("origin")
    private String origin = "";
    @SerializedName("timeZone")
    private String timeZone = "";
    @SerializedName("timeout")
    private Integer timeout = 15;
    @SerializedName("boot")
    private Boolean boot = false;
    @SerializedName("groups")
    private List<JsonElement> groups = new ArrayList<>();

    public static Live from(JsonElement element) {
        try {
            Live live = GsonHolder.GSON.fromJson(element, Live.class);
            if (live == null) live = new Live();
            if (element.isJsonObject() && element.getAsJsonObject().has("ext")) {
                JsonElement extEl = element.getAsJsonObject().get("ext");
                if (extEl.isJsonPrimitive()) live.ext = extEl.getAsString();
                else live.ext = extEl.toString();
            }
            return live;
        } catch (Exception e) {
            return new Live();
        }
    }

    public String getName() {
        return name == null ? "" : name;
    }

    public Integer getType() {
        return type == null ? 0 : type;
    }

    public String getUrl() {
        return url == null ? "" : url;
    }

    public String getApi() {
        return api == null ? "" : api;
    }

    public String getExt() {
        return ext == null ? "" : ext;
    }

    public String getJar() {
        return jar == null ? "" : jar;
    }

    public String getEpg() {
        return epg == null ? "" : epg;
    }

    public String getLogo() {
        return logo == null ? "" : logo;
    }

    public String getUa() {
        return ua == null ? "" : ua;
    }

    public String getReferer() {
        return referer == null ? "" : referer;
    }

    public String getOrigin() {
        return origin == null ? "" : origin;
    }

    public String getTimeZone() {
        return timeZone == null ? "" : timeZone;
    }

    public Integer getTimeout() {
        return timeout == null || timeout < 1 ? 15 : timeout;
    }

    public Boolean getBoot() {
        return boot != null && boot;
    }

    public List<JsonElement> getGroups() {
        return groups == null ? new ArrayList<>() : groups;
    }
}
