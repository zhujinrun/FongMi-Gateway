package com.github.catvod;

import com.github.catvod.net.OkHttp;

import java.util.Map;

public class Proxy {

    private static final int PORT = 9978;

    public static String getUrl() {
        return "http://127.0.0.1:" + getPort() + "/proxy";
    }

    public static String getUrl(String param) {
        return getUrl() + "?" + param;
    }

    public static int getPort() {
        return PORT;
    }

    public static Object[] proxy(Map<String, String> params) throws Exception {
        return null;
    }
}
