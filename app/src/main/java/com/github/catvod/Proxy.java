package com.github.catvod;

import java.util.Map;

public class Proxy {

    private static volatile int PORT = 9979;

    public static void setPort(int port) {
        if (port > 0) PORT = port;
    }

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
