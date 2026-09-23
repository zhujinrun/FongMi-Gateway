package com.github.catvod.bean;

import java.util.ArrayList;
import java.util.List;

public class Doh {
    private String name;
    private String url;
    private List<String> ips = new ArrayList<>();

    public static List<Doh> arrayFrom(com.google.gson.JsonElement el) {
        return new ArrayList<>();
    }

    public static List<Doh> get(android.content.Context context) {
        return new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    public List<String> getIps() {
        return ips;
    }
}
