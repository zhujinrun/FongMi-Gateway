package com.github.catvod.bean;

import java.util.ArrayList;
import java.util.List;

public class Header {
    private String host;
    private java.util.Map<String, String> header = new java.util.HashMap<>();

    public static List<Header> arrayFrom(String json) {
        return new ArrayList<>();
    }

    public static List<Header> arrayFrom(com.google.gson.JsonElement el) {
        return new ArrayList<>();
    }

    public String getHost() {
        return host;
    }

    public java.util.Map<String, String> getHeader() {
        return header;
    }
}
